// Experiment 2 - the AI-to-human handover study - deployed into its own resource group.
//
// Self-contained: this creates everything the study needs - registry, identity, logs, the Container
// Apps environment, a Postgres server, and the container app itself. It deliberately does NOT share
// the pre-test's platform. The two live in different resource groups so that the pre-test's data
// survives this study being deployed, resized or deleted, and so this study does not inherit a
// database server that a finished pre-test is about to have torn down.
//
// Resource-group scoped, so the resource group is created by hand (or by `az group create`) and
// this template never owns it - a `what-if` or a failed deploy can therefore never delete the
// group or the database inside it. Deploy with scripts/release.sh, which builds and tags the image
// first: containerImage has no default, so a deploy cannot silently ship a stale one.
//
// What this study needs that the pre-test did not:
//   - an OpenAI key, for the voice leg (the browser talks to the Realtime API directly, with an
//     ephemeral token minted by the container; the long-lived key never leaves the server)
//   - a second credential for the human agent's console, separate from the researcher's, so the
//     confederate can read their queue without also being able to wipe the database

@description('Container app name, and the base name every other resource name is derived from. Changing it after the first deploy creates a NEW, empty Postgres server.')
param appName string = 'exp2-feeling-heard'

@description('Container image to run. Required unless deployApp is false - scripts/release.sh passes a git-SHA-tagged image.')
param containerImage string = ''

@description('Set false to deploy only the long-lived platform and not the container app. Needed exactly once, on a brand-new resource group: the image has to be pushed to a registry that does not exist yet, so the registry is created first and the app follows on the next deploy. scripts/release.sh does this for you.')
param deployApp bool = true

@description('Azure region. Defaults to the resource group\'s, which for this study should be Switzerland North: the ethics application commits to holding research data on ETH infrastructure.')
param location string = resourceGroup().location

@description('Globally-unique ACR name (lowercase alphanumeric, 5-50 chars).')
param acrName string = 'acr${uniqueString(subscription().id, resourceGroup().name)}'

@description('Port the container listens on (Spring Boot serves UI + API here).')
param targetPort int = 8080

@description('PostgreSQL admin username.')
param dbAdminUser string = 'portaladmin'

@description('PostgreSQL admin password. You choose this on the first deploy and it is then fixed for the life of the server: 8-128 chars, at least 3 of upper/lower/digit/special.')
@secure()
param dbAdminPassword string

@description('Application database name. Its own database, not just its own schema: both studies name their study schema `behavioural`, so sharing a database would put two different versions of that schema on a collision course.')
param dbName string = 'exp2_ai_human_handover'

@description('Shared secret Qualtrics must send as X-Handover-Secret when registering a handover. Empty = no check. Set it for a real run: without it, anyone who finds the URL can mint a token and enter the study in a condition of their choosing, and those responses look exactly like real ones in the data.')
@secure()
param handoverSecret string = ''

@description('Username for the researcher data view at /data.')
param researcherUser string = 'researcher'

@description('Password for the researcher data view at /data, the behavioural log behind it, and the destructive /api/admin endpoints. Empty = those paths are CLOSED (not open). Everything participants touch stays open either way.')
@secure()
param researcherPassword string = ''

@description('Username the human agent signs into their console with.')
param agentUser string = 'agent'

@description('Password for the human agent\'s console and queue. A SEPARATE credential from the researcher\'s on purpose: the confederate needs to read who is waiting and what the AI already heard, and does not need to be able to wipe the study. Empty = the console is CLOSED, which stops the handover leg working - set it before a session.')
@secure()
param agentPassword string = ''

@description('OpenAI / Azure OpenAI API key, used server-side to mint ephemeral Realtime tokens. Empty = no voice: the container starts and the portal works, but /api/voice/token fails and the AI leg of the call cannot begin.')
@secure()
param openAiApiKey string = ''

@description('Realtime API base URL the backend mints ephemeral tokens against. The token endpoint is <base>/realtime/client_secrets. Empty keeps the application default (the MTEC Azure OpenAI deployment).')
param openAiBaseUrl string = ''

@description('Entra tenant ID used for Easy Auth.')
param tenantId string = tenant().tenantId

@description('Entra app registration (client) ID. Leave empty for the real study run - Easy Auth blocks anonymous Prolific participants and breaks the Qualtrics iframe.')
param authClientId string = ''

@description('Entra app registration client secret.')
@secure()
param authClientSecret string = ''

@description('If true, every request must be authenticated. Only meaningful when authClientId is set.')
param requireAuthentication bool = true

// Long-lived: registry, identity, logs, Container Apps environment, Postgres server.
module platform 'modules/platform.bicep' = {
    name: 'platform-deployment'
    params: {
        location: location
        namePrefix: appName
        acrName: acrName
        dbAdminUser: dbAdminUser
        dbAdminPassword: dbAdminPassword
        dbName: dbName
    }
}

// The deployable that changes every release. Everything study-specific is composed here and passed
// in as plain env/secret settings; the module itself is generic.
module exp2App 'modules/containerapp.bicep' = if (deployApp) {
    name: 'exp2-app-deployment'
    params: {
        location: location
        appName: appName
        containerImage: containerImage
        targetPort: targetPort
        environmentId: platform.outputs.environmentId
        acrLoginServer: platform.outputs.acrLoginServer
        uamiId: platform.outputs.uamiId
        env: concat([
            {
                name: 'SPRING_DATASOURCE_URL'
                value: 'jdbc:postgresql://${platform.outputs.pgFqdn}:5432/${platform.outputs.dbName}?sslmode=require'
            }
            { name: 'SPRING_DATASOURCE_USERNAME', value: dbAdminUser }
            { name: 'RESEARCHER_USER', value: researcherUser }
            { name: 'AGENT_USER', value: agentUser }
        ], empty(openAiBaseUrl) ? [] : [
            // Only sent when set, so an unset value leaves the application default in place rather
            // than overriding it with an empty string - which would fail at the first token mint
            // with a URL error rather than a missing-configuration one.
            { name: 'OPENAI_BASE_URL', value: openAiBaseUrl }
        ])
        // Empty values are dropped by the module, so an unset secret simply does not reach the
        // container and the application default applies.
        secretEnv: {
            SPRING_DATASOURCE_PASSWORD: dbAdminPassword
            HANDOVER_SECRET: handoverSecret
            RESEARCHER_PASSWORD: researcherPassword
            AGENT_PASSWORD: agentPassword
            OPENAI_API_KEY: openAiApiKey
        }
        tenantId: tenantId
        authClientId: authClientId
        authClientSecret: authClientSecret
        requireAuthentication: requireAuthentication
        // Lets Qualtrics register a handover server-side even with Easy Auth on, for a locked-down
        // build. It only opens that one call - participants still hit the SPA anonymously - so a
        // real study run needs Easy Auth off entirely.
        authExcludedPaths: [ '/api/handover' ]
    }
}

output appUrl string = deployApp ? 'https://${exp2App!.outputs.appFqdn}' : ''
output handoverRegisterUrl string = deployApp ? 'https://${exp2App!.outputs.appFqdn}/api/handover' : ''
output researcherDataUrl string = deployApp ? 'https://${exp2App!.outputs.appFqdn}/data' : ''
output acrName string = platform.outputs.acrName
output pgFqdn string = platform.outputs.pgFqdn
output dbName string = platform.outputs.dbName
