// The long-lived half of the deployment: registry, identity, logs, the Container Apps environment
// and the database server. Created once and changed rarely, unlike the container app and its
// image, which change on every commit and live in containerapp.bicep.
//
// This study stands up its own platform rather than sharing one with levels-of-ai-help. They run
// in different resource groups and, most likely, different windows; sharing would couple their
// lifecycles - a database server resized or a registry emptied for one would land on the other
// mid-run - for no saving that matters at this scale.

@description('Azure region.')
param location string

@description('Base name the platform resource names are derived from. Changing it after the first deploy creates a NEW, empty Postgres server - the server name hashes this value.')
param namePrefix string

@description('Globally-unique ACR name (lowercase alphanumeric, 5-50 chars).')
param acrName string

@description('PostgreSQL admin username.')
param dbAdminUser string

@description('PostgreSQL admin password (8-128 chars; 3 of upper/lower/digit/special).')
@secure()
param dbAdminPassword string

@description('Application database name. The portal schema and the behavioural schema both live in it, each migrated by its own Flyway instance.')
param dbName string

var logAnalyticsName = 'log-${namePrefix}'
var environmentName  = 'cae-${namePrefix}'
var acrPullRoleId    = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')
var pgServerName     = 'pg${uniqueString(resourceGroup().id, namePrefix)}'

resource acr 'Microsoft.ContainerRegistry/registries@2023-11-01-preview' = {
    name: acrName
    location: location
    sku: { name: 'Basic' }
    properties: {
        adminUserEnabled: false   // use managed identity, not admin creds
    }
}

resource uami 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
    name: 'id-${namePrefix}'
    location: location
}

// Log sink the Container Apps environment requires
resource logAnalytics 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
    name: logAnalyticsName
    location: location
    properties: {
        sku: { name: 'PerGB2018' }
        retentionInDays: 30
    }
}

resource env 'Microsoft.App/managedEnvironments@2024-03-01' = {
    name: environmentName
    location: location
    properties: {
        appLogsConfiguration: {
            destination: 'log-analytics'
            logAnalyticsConfiguration: {
                customerId: logAnalytics.properties.customerId
                sharedKey: logAnalytics.listKeys().primarySharedKey
            }
        }
    }
}

// PostgreSQL Flexible Server - the insurance_portal + behavioural schemas live here, each created
// by its own Flyway instance (the portal library ships the portal migrations, pretest-app the
// behavioural ones), so neither owner has to coordinate migration versions with the other.
//
// Burstable B1ms: the smallest tier that comfortably holds a few hundred participants' logs. It is
// the main monthly cost in this resource group, so delete the group when the pre-test is finished
// rather than leaving it idling.
resource pg 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
    name: pgServerName
    location: location
    sku: {
        name: 'Standard_B1ms'
        tier: 'Burstable'
    }
    properties: {
        version: '16'
        administratorLogin: dbAdminUser
        administratorLoginPassword: dbAdminPassword
        storage: { storageSizeGB: 32 }
        backup: { backupRetentionDays: 7, geoRedundantBackup: 'Disabled' }
        highAvailability: { mode: 'Disabled' }
        network: { publicNetworkAccess: 'Enabled' }
    }
}

resource pgDb 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = {
    parent: pg
    name: dbName
    properties: {
        charset: 'UTF8'
        collation: 'en_US.utf8'
    }
}

// Allow access from Azure services (incl. the Container App's egress IP, which is NOT
// the env staticIp and isn't a single pinnable IP for Consumption envs without a VNet).
// Public endpoint, but still SSL + password gated. Full lockdown later = VNet integration.
resource pgAllowAzure 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
    parent: pg
    name: 'AllowAllAzureServices'
    properties: {
        startIpAddress: '0.0.0.0'
        endIpAddress: '0.0.0.0'
    }
}

// Grant the identity pull rights on the registry (no passwords). Lives here rather than with the
// app: both the identity and the registry are platform. The app module depends on this one, so ARM
// completes the grant before any container tries to start.
resource acrPull 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
    name: guid(acr.id, uami.id, acrPullRoleId)
    scope: acr
    properties: {
        roleDefinitionId: acrPullRoleId
        principalId: uami.properties.principalId
        principalType: 'ServicePrincipal'
    }
}

output acrName string = acr.name
output acrLoginServer string = acr.properties.loginServer
output uamiId string = uami.id
output environmentId string = env.id
output pgServerName string = pg.name
output pgFqdn string = pg.properties.fullyQualifiedDomainName
output dbName string = pgDb.name
