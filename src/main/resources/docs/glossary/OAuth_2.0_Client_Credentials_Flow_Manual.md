# OAuth 2.0 Client Credentials Flow Manual

## Overview
OAuth 2.0 Client Credentials Flow is used when a client application (such as a backend service) needs to authenticate and request access to resources without user interaction. This flow is typically used for machine-to-machine (M2M) authentication.

## Prerequisites / Assumptions
Before making requests, ensure you have:
- A valid **client_id** and **client_secret**.
- This example assumes the authorization server (Keycloak) is running on **localhost:7070**. Replace this with the actual auth server URL.
- A realm needs to been configured (e.g. 'master') and respective endpoint available: `/realms/master/protocol/openid-connect/token`.

## 1. Requesting an Access Token
To obtain an access token, send a **POST** request to the token endpoint with the following details.

### **Request**
```
POST /realms/master/protocol/openid-connect/token HTTP/1.1
Host: localhost:7070
Content-Type: application/x-www-form-urlencoded
Authorization: Basic Og==
Content-Length: 104

client_id=<client_id>&client_secret=<client_secret>&grant_type=client_credentials
```

### **Explanation of Parameters**
| Parameter         | Description |
|------------------|-------------|
| `client_id`      | The unique identifier for your client application. |
| `client_secret`  | The secret key assigned to your client. |
| `grant_type`     | Must be set to `client_credentials` to indicate this authentication flow. |

### **Example cURL Command**
```sh
curl -X POST "http://localhost:7070/realms/master/protocol/openid-connect/token" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     -u "open-bank-project:WWJ04UzMhWmLEqW2KIgBHwD4UNEotzXz" \
     -d "grant_type=client_credentials"
```

## 2. Expected Response
A successful request will return a JSON response containing the access token:
```json
{
    "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "token_type": "Bearer",
    "expires_in": 3600
}
```

### **Response Fields**
| Field           | Description |
|----------------|-------------|
| `access_token` | The token required to authenticate API requests. |
| `token_type`   | Usually `Bearer`, meaning it should be included in the Authorization header. |
| `expires_in`   | The token expiration time in seconds. |

## 3. Using the Access Token
Once you obtain the access token, include it in the `Authorization` header of your subsequent API requests:

### **Example API Request with Token**
```sh
curl -X GET "http://localhost:7070/protected/resource" \
     -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```
