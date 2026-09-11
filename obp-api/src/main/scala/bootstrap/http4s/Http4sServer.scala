package bootstrap.http4s

import cats.effect._
import code.api.util.APIUtil
import code.api.util.http4s.{Http4sApp, Http4sConfigUtil}
import code.util.Helper.MdcLoggable
import com.comcast.ip4s._
import org.http4s.ember.server._

object Http4sServer extends IOApp with MdcLoggable {

  //Start OBP relevant objects and settings; this step MUST be executed first
  // new bootstrap.http4s.Http4sBoot().boot
  new bootstrap.liftweb.Boot().boot

  // A `migrator` instance has done its entire job in Boot above — schema, migrations, seed data —
  // and must not serve traffic. Exit here, BEFORE Http4sApp.httpApp below, so a one-shot Job does
  // not pay for building the whole ResourceDoc registry it will never serve. The shutdown hook
  // registered during boot still runs on System.exit, so the connection pool and Redis close
  // cleanly and the Job ends successfully rather than being reaped.
  //
  // Only `migrator` exits: a `scheduler` instance keeps running (its schedulers live in the
  // actor system started by Boot) and still binds a port, so it has an endpoint for probes even
  // though no Service routes traffic to it.
  if (code.api.Constant.InstanceRole.exitsAfterBoot) {
    logger.info(s"instance.role=${code.api.Constant.InstanceRole.value}: boot complete, " +
      s"exiting without binding an HTTP port.")
    System.exit(0)
  }

  // Get bind address: use bind_address prop if set, otherwise parse from hostname
  // Note: hostname prop must remain unchanged as it may be used for local_provider_name fallback
  val host =  Http4sConfigUtil.parseHostname(APIUtil.getPropsValue("bind_address",code.api.Constant.HostName))
  val port = APIUtil.getPropsAsIntValue("dev.port",8080)

  // Use shared httpApp configuration (same as tests)
  val httpApp = Http4sApp.httpApp

  override def run(args: List[String]): IO[ExitCode] = {
    // Force the peer-trust configuration at boot. It is a lazy val first needed when a request
    // carries certificate material, so without this an unparseable mtls.trusted_proxy.N DN (logged
    // at ERROR, proxy silently untrusted) would surface mid-traffic instead of in the boot log.
    code.api.util.PeerTrust.config

    val builder = EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(host).get)
      .withPort(Port.fromInt(port).get)
    val configuredBuilder = if (Http4sMtls.enabled) {
      logger.info(s"mTLS termination is ENABLED: serving HTTPS on port $port, " +
        s"client_auth=${if (Http4sMtls.config.needClientAuth) "need" else "want"}, " +
        s"keystore=${Http4sMtls.config.keystorePath}, truststore=${Http4sMtls.config.truststorePath}")
      if (code.api.Constant.HostName.startsWith("http://"))
        logger.warn("mtls.enabled=true but the hostname prop still starts with http:// — set it to https:// so generated links match the TLS listener.")
      // No certificate middleware here: Http4sApp.httpApp resolves the caller for every request,
      // TLS or not (code.api.util.http4s.CallerCertificate). Ember exposes the handshake
      // certificate on the request, which is all this branch needs to contribute.
      builder
        .withTLS(Http4sMtls.tlsContext, Http4sMtls.tlsParameters)
        .withHttpApp(httpApp)
    } else {
      builder.withHttpApp(httpApp)
    }
    configuredBuilder.build
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }
}
