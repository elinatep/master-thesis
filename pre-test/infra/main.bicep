// The feeling-heard pre-test, deployed into its own resource group.
//
// Self-contained: this creates everything the study needs - registry, identity, logs, the Container
// Apps environment, a Postgres server, and the container app itself. It does NOT share the platform
// levels-of-ai-help deploys. The two studies live in different resource groups and run in different
// windows, so coupling their lifecycles would mean a server resized or a registry emptied for one
// landing on the other mid-run, for no saving worth having at this scale.
//
// Resource-group scoped, so the resource group is created by hand (or by `az group create`) and
// this template never owns it. Deploy with scripts/release.ps1, which builds and tags the image
// first - containerImage has no default, so a deploy cannot silently ship a stale one.

@description('Container app name, and the base name every other resource name is derived from. Changing it after the first deploy creates a NEW, empty Postgres server.')
param appName string = 'pretest-feeling-heard'

@description('Container image to run. Required unless deployApp is false - scripts/release.ps1 passes a git-SHA-tagged image.')
param containerImage string = ''

@description('Set false to deploy only the long-lived platform and not the container app. Needed exactly once, on a brand-new resource group: the image has to be pushed to a registry that does not exist yet, so the registry is created first and the app follows on the next deploy. scripts/release.ps1 does this for you.')
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

@description('Application database name.')
param dbName string = 'pretest_feeling_heard'

@description('Shared secret Qualtrics must send as X-Handover-Secret when registering a handover. Empty = no check. Set it for a real run: without it, anyone who finds the URL can mint a token and enter the study in an arm of their choosing, and those responses look exactly like real ones in the data.')
@secure()
param handoverSecret string = ''

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
module pretestApp 'modules/containerapp.bicep' = if (deployApp) {
    name: 'pretest-app-deployment'
    params: {
        location: location
        appName: appName
        containerImage: containerImage
        targetPort: targetPort
        environmentId: platform.outputs.environmentId
        acrLoginServer: platform.outputs.acrLoginServer
        uamiId: platform.outputs.uamiId
        env: [
            {
                name: 'SPRING_DATASOURCE_URL'
                value: 'jdbc:postgresql://${platform.outputs.pgFqdn}:5432/${platform.outputs.dbName}?sslmode=require'
            }
            { name: 'SPRING_DATASOURCE_USERNAME', value: dbAdminUser }
        ]
        // Empty values are dropped by the module, so an unset handover secret simply does not reach
        // the container. There is no OpenAI key: the pre-test has no voice interaction.
        secretEnv: {
            SPRING_DATASOURCE_PASSWORD: dbAdminPassword
            HANDOVER_SECRET: handoverSecret
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

output appUrl string = deployApp ? 'https://${pretestApp!.outputs.appFqdn}' : ''
output handoverRegisterUrl string = deployApp ? 'https://${pretestApp!.outputs.appFqdn}/api/handover' : ''
output researcherDataUrl string = deployApp ? 'https://${pretestApp!.outputs.appFqdn}/data' : ''
output acrName string = platform.outputs.acrName
output pgFqdn string = platform.outputs.pgFqdn
output dbName string = platform.outputs.dbName
