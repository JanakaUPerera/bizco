# Bizco TLS Deployment Guide

## 1. Requirement

`SRS.md` Section 7.2 and Section 6.22.7 require TLS **1.3** specifically for JavaFX-to-server
communication, not just "some TLS version." This guide covers enabling it for a LAN or
production deployment. It does not change default local-development behavior: `mvn clean
verify`, Testcontainers integration tests, and `mvn spring-boot:run` without a profile all
continue to run over plain HTTP, exactly as before.

## 2. Server: generate a keystore

### 2.1 Self-signed certificate for an internal LAN deployment

Self-signed is normal and expected for an SME's internal network — there is no public DNS name
to get a CA-issued certificate for. Generate a PKCS12 keystore with `keytool` (ships with the
JDK):

```text
keytool -genkeypair -alias bizco -keyalg RSA -keysize 2048 -validity 3650 \
  -storetype PKCS12 -keystore bizco-server.p12 \
  -dname "CN=bizco-server, OU=Bizco, O=Bizco, L=Colombo, ST=Western, C=LK" \
  -ext "SAN=dns:bizco-server,ip:<server-lan-ip>"
```

Replace `<server-lan-ip>` with the server machine's actual LAN IP (or hostname all clients will
use). Store the resulting `bizco-server.p12` outside the repository and outside any Git-tracked
directory — treat it the same as a database password.

### 2.2 CA-issued certificate

If the business has a real domain and internet-facing deployment, use a standard CA (or an
internal PKI) instead and import the issued cert/key into a PKCS12 keystore the same way. The
Spring Boot configuration below does not care which path the certificate came from.

## 3. Server: enable the `tls` profile

Set these environment variables (in `.env`, not committed) alongside the existing
`BIZCO_DATASOURCE_*` variables:

```text
SPRING_PROFILES_ACTIVE=tls
BIZCO_TLS_KEYSTORE_PATH=/absolute/path/to/bizco-server.p12
BIZCO_TLS_KEYSTORE_PASSWORD=<keystore-password>
BIZCO_TLS_KEYSTORE_TYPE=PKCS12
BIZCO_TLS_KEY_ALIAS=bizco
SERVER_PORT=8443
```

`application-tls.yml` (`bizco-server/src/main/resources/`) is the profile that reads these and
sets `server.ssl.enabled-protocols: TLSv1.3`. Starting the server with this profile active serves
HTTPS on the configured port instead of HTTP; there is no dual HTTP+HTTPS mode. Update
`BIZCO_SERVER_URL` on every JavaFX client (see `bizco-client` `ServerConfig`) to
`https://<server-lan-ip>:8443/`.

## 4. Client: trusting a self-signed certificate

`bizco-client` uses `java.net.http.HttpClient` with the JVM's default trust store. A self-signed
certificate is not in that trust store by default, so JavaFX clients need one of:

### 4.1 Import the certificate into each client's JVM trust store (recommended for LAN)

Export the public certificate from the server keystore once:

```text
keytool -exportcert -alias bizco -keystore bizco-server.p12 -storetype PKCS12 -file bizco-server.crt
```

Then, on every client machine, import it into the JVM `cacerts` trust store bundled with the
JavaFX client's runtime:

```text
keytool -importcert -alias bizco-server -file bizco-server.crt \
  -keystore <jre-path>/lib/security/cacerts -storepass changeit -noprompt
```

This is a one-time step per client install and belongs in the installation script/checklist
(`DevelopmentPlan.md` Appendix C, Week 20 Installation Guide), not in application code.

### 4.2 Per-launch trust store override (no reinstall needed)

Alternatively, point the JVM at a dedicated trust store containing only the Bizco server
certificate, without touching the bundled `cacerts`:

```text
java -Djavax.net.ssl.trustStore=bizco-client-truststore.p12 \
     -Djavax.net.ssl.trustStorePassword=<password> \
     -jar bizco-client.jar
```

Build `bizco-client-truststore.p12` with the same `keytool -importcert` command as above, just
targeting a fresh keystore file instead of the shared `cacerts`.

## 5. Verifying TLS 1.3 is actually negotiated

```text
openssl s_client -connect <server-lan-ip>:8443 -tls1_3
```

A successful handshake confirms `enabled-protocols: TLSv1.3` took effect. If the server instead
rejects the connection, confirm the `tls` profile is active (check startup logs for `Tomcat
started on port(s): 8443 (https)`) and that the keystore path/password/alias are correct.

## 6. Release-gate checklist

Before Week 19/20 release hardening (`DevelopmentPlan.md` Section 14.6):

```text
[ ] production keystore generated and stored outside Git
[ ] tls profile active on the deployed server
[ ] TLS 1.3 confirmed with openssl s_client
[ ] every client machine trusts the server certificate
[ ] BIZCO_SERVER_URL on every client uses https://
[ ] keystore password stored in the same secret-handling process as DB credentials
```
