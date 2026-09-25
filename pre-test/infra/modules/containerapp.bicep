// One deployable running on the shared platform: the container app, its configuration, and
// (optionally) Easy Auth in front of it. Everything here changes on every release - the image tag
// most of all.
//
// Deliberately knows nothing about what it is running. No Postgres, no OpenAI, no handover: the
// caller composes those into `env` and `secretEnv`, so this file is reusable by any deployable that
// wants a container on the shared environment.
//
// This is a COPY of the module in levels-of-ai-help, not a fork. It is generic on purpose, so a
// second deployable in a second repository uses the same file with its own settings. If it ever
// needs to change, change it there and copy it here - a divergence would mean the two studies run
// on subtly different container configuration without anyone deciding that they should.

@description('Azure region.')
param location string

@description('Container app name. Also the container name inside it.')
param appName string

@description('Container image to run. Required - the release script passes a SHA-tagged image.')
param containerImage string

@description('Port the container listens on (Spring Boot serves UI + API here).')
param targetPort int

// --- shared platform, passed in as outputs of platform.bicep ---
param environmentId string
param acrLoginServer string
param uamiId string

@description('Plain environment variables, as a list of { name, value } objects.')
param env array = []

@description('Secret environment variables, as { ENV_NAME: value }. Each becomes a container app secret referenced by the env var of the same name; entries with an empty value are dropped, so an unset secret simply does not reach the container.')
@secure()
param secretEnv object = {}

// --- Easy Auth (optional) ---
param tenantId string
param authClientId string = ''
@secure()
param authClientSecret string = ''
param requireAuthentication bool = true

@description('Paths that bypass authentication when Easy Auth is on (e.g. a server-to-server callback).')
param authExcludedPaths array = []

@description('Warm replicas. Keep at least 1: a cold start reads as a broken website to a participant.')
param minReplicas int = 1

@description('Maximum replicas. The study is stateless per request, but the default stays 1 for parity with the other deployable.')
param maxReplicas int = 1

var deployAuth = !empty(authClientId)

// Secret name is derived from the env var name: SPRING_DATASOURCE_PASSWORD -> spring-datasource-password
// (container app secret names must be lowercase alphanumeric with dashes).
var presentSecrets = filter(items(secretEnv), entry => !empty(entry.value))
var secretDefinitions = map(presentSecrets, entry => {
    name: toLower(replace(entry.key, '_', '-'))
    value: entry.value
})
var secretEnvVars = map(presentSecrets, entry => {
    name: entry.key
    secretRef: toLower(replace(entry.key, '_', '-'))
})

resource app 'Microsoft.App/containerApps@2024-03-01' = {
    name: appName
    location: location
    identity: {
        type: 'UserAssigned'   // identity used to pull from ACR
        userAssignedIdentities: {
            '${uamiId}': {}
        }
    }
    properties: {
        managedEnvironmentId: environmentId
        configuration: {
            ingress: {
                external: true
                targetPort: targetPort
                transport: 'auto'
                traffic: [ { latestRevision: true, weight: 100 } ]
            }
            registries: [
                {
                server: acrLoginServer
                identity: uamiId
                }
            ]
            secrets: concat(
                deployAuth ? [ { name: 'aad-client-secret', value: authClientSecret } ] : [],
                secretDefinitions
            )
        }
        template: {
            containers: [
                {
                name: appName
                image: containerImage
                resources: {
                    cpu: json('0.5')
                    memory: '1Gi'
                }
                env: concat(env, secretEnvVars)
                }
            ]
        // min 1 keeps a warm replica. No voice here, but a cold start still lands on a
        // participant as a stalled website - and in the arm where the website failing IS the
        // manipulation, an accidental stall is a confound.
        scale: {
            minReplicas: minReplicas
            maxReplicas: maxReplicas
        }
        }
    }
}

// Easy Auth — only deployed once an app registration exists (breaks the loop)
resource authConfig 'Microsoft.App/containerApps/authConfigs@2024-03-01' = if (deployAuth) {
    parent: app
    name: 'current'
    properties: {
        platform: { enabled: true }
        globalValidation: {
            unauthenticatedClientAction: requireAuthentication ? 'RedirectToLoginPage' : 'AllowAnonymous'
            redirectToProvider: 'azureactivedirectory'
            excludedPaths: authExcludedPaths
        }
        identityProviders: {
            azureActiveDirectory: {
                enabled: true
                registration: {
                    openIdIssuer: '${environment().authentication.loginEndpoint}${tenantId}/v2.0'
                    clientId: authClientId
                    clientSecretSettingName: 'aad-client-secret'
                }
                validation: {
                    allowedAudiences: [ 'api://${authClientId}' ]
                }
            }
        }
    }
}

output appFqdn string = app.properties.configuration.ingress.fqdn
