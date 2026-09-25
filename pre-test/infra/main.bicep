// The feeling-heard pre-test, deployed onto the platform levels-of-ai-help owns.
//
// Resource-group scoped, deliberately. Everything shared - the registry, the managed identity, the
// Log Analytics workspace, the Container Apps environment, the Postgres server - is referenced
// here as `existing` and never declared. Only one repository may own those, and it is not this one;
// declaring them again would mean two deployments taking turns to overwrite each other's idea of
// what they should look like.
//
// What this file does create is exactly two things: a database of its own on the shared server,
// and a container app to run in. Both belong to this study alone.

@description('Container app name. Also the container name inside it.')
param appName string = 'pretest-feeling-heard'

@description('Base name the SHARED platform resources are derived from. Must match the value levels-of-ai-help deploys with, or the existing-resource lookups below will not find them.')
param platformNamePrefix string = 'insurance-portal'

@description('Container image to run. Required - scripts/release.ps1 passes a SHA-tagged image. No default, so a deploy can never silently ship a stale one.')
param containerImage string

@description('Azure region. Must be the region the shared platform is in.')
param location string = resourceGroup().location

@description('Port the container listens on (Spring Boot serves UI + API here).')
param targetPort int = 8080

@description('This study OWN database on the shared Postgres server. Not the portal study database: both name their study schema `behavioural`, and sharing one database would put two different schemas and two Flyway histories on a collision course.')
param dbName string = 'pretest_feeling_heard'

@description('PostgreSQL admin username, as the shared server was created with.')
param dbAdminUser string = 'portaladmin'

@description('PostgreSQL admin password (the shared server\'s).')
@secure()
param dbAdminPassword string

@description('Shared secret Qualtrics must send as X-Handover-Secret when registering a handover. Empty = no check. Set it for a real run: without it, anyone who finds the URL can mint a handover token and enter the study in an arm of their choosing.')
@secure()
param handoverSecret string = ''

@description('Entra tenant ID used for Easy Auth.')
param tenantId string = tenant().tenantId

@description('Entra app registration (client) ID. Leave empty for the real study run - Easy Auth blocks anonymous Prolific participants.')
param authClientId string = ''

@description('Entra app registration client secret.')
@secure()
param authClientSecret string = ''

@description('If true, every request must be authenticated. Only meaningful when authClientId is set.')
param requireAuthentication bool = true

// --- the shared platform, referenced read-only -------------------------------
//
// Names are derived exactly as platform.bicep derives them, so these resolve to the resources that
// module created. If a lookup fails, the platform has not been deployed into this resource group,
// or platformNamePrefix does not match what it was deployed with.

resource containerAppsEnv 'Microsoft.App/managedEnvironments@2024-03-01' existing = {
    name: 'cae-${platformNamePrefix}'
}

resource uami 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' existing = {
    name: 'id-${platformNamePrefix}'
}

resource acr 'Microsoft.ContainerRegistry/registries@2023-11-01-preview' existing = {
    name: 'acr${uniqueString(subscription().id, resourceGroup().name)}'
}

resource pg 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' existing = {
    name: 'pg${uniqueString(resourceGroup().id, platformNamePrefix)}'
}

// --- what this study owns ----------------------------------------------------

// A child of the shared server, which leaves the server itself untouched. Additive: creating it
// does not alter the database the other deployable uses.
resource pretestDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
    parent: pg
    name: dbName
    properties: {
        charset: 'UTF8'
        collation: 'en_US.utf8'
    }
}

module pretestApp 'modules/containerapp.bicep' = {
    name: 'pretest-app-deployment'
    params: {
        location: location
        appName: appName
        containerImage: containerImage
        targetPort: targetPort
        environmentId: containerAppsEnv.id
        acrLoginServer: acr.properties.loginServer
        uamiId: uami.id
        env: [
            {
                name: 'SPRING_DATASOURCE_URL'
                value: 'jdbc:postgresql://${pg.properties.fullyQualifiedDomainName}:5432/${dbName}?sslmode=require'
            }
            { name: 'SPRING_DATASOURCE_USERNAME', value: dbAdminUser }
        ]
        // Empty values are dropped by the module, so an unset handover secret simply does not
        // reach the container. There is no OpenAI key here: the pre-test has no voice interaction.
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
    dependsOn: [ pretestDb ]
}

output appFqdn string = pretestApp.outputs.appFqdn
output appUrl string = 'https://${pretestApp.outputs.appFqdn}'
output handoverRegisterUrl string = 'https://${pretestApp.outputs.appFqdn}/api/handover'
output acrName string = acr.name
output acrLoginServer string = acr.properties.loginServer
output dbName string = dbName
