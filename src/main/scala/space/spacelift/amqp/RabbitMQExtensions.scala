package space.spacelift.amqp

import java.io.{BufferedReader, DataOutputStream, InputStreamReader}
import java.net.{HttpURLConnection, URL, URLEncoder}
import java.util.Base64

object RabbitMQExtensions {
  def federateExchange(upstreamURI: String, downstreamHost: String, downstreamUser: String, downstreamPass: String, upstreamName: String, vhost: String, pattern: String) = federate(upstreamURI, downstreamHost, downstreamUser, downstreamPass, upstreamName, vhost, pattern, "exchanges")

  def federateQueue(upstreamURI: String, downstreamHost: String, downstreamUser: String, downstreamPass: String, upstreamName: String, vhost: String, pattern: String) = federate(upstreamURI, downstreamHost, downstreamUser, downstreamPass, upstreamName, vhost, pattern, "queues")

  private def isExistingFederation(downstreamHost: String, downstreamUser: String, downstreamPass: String, upstreamName: String, vhost: String): Boolean = {
    val uri = s"$downstreamHost/api/parameters/federation-upstream/" + URLEncoder.encode(vhost, "utf-8") + "/" + URLEncoder.encode(upstreamName, "utf-8")

    get(uri, downstreamUser, downstreamPass)
  }

  private def federate(upstreamURI: String, downstreamHost: String, downstreamUser: String, downstreamPass: String, upstreamName: String, vhost: String, pattern: String, applyTo: String) = {
    val uri = s"$downstreamHost/api/parameters/federation-upstream/" + URLEncoder.encode(vhost, "utf-8") + "/" + URLEncoder.encode(upstreamName, "utf-8")

    val data = s"""{"value":{"uri":"$upstreamURI"}}"""

    (isExistingFederation(downstreamHost, downstreamUser, downstreamPass, upstreamName, vhost) || put(uri, data, downstreamUser, downstreamPass)) &&
      createPolicy(downstreamHost, downstreamUser, downstreamPass, vhost, pattern, upstreamName, applyTo)
  }

  private def isExistingPolicy(downstreamHost: String, downstreamUser: String, downstreamPass: String, vhost: String, upstreamName: String) = {
    val uri = s"$downstreamHost/api/policies/${URLEncoder.encode(vhost, "utf-8")}/${URLEncoder.encode(upstreamName, "utf-8")}"

    get(uri, downstreamUser, downstreamPass)
  }

  private def createPolicy(downstreamHost: String, downstreamUser: String, downstreamPass: String, vhost: String, pattern: String, upstreamName: String, applyTo: String) = {
    val uri = s"$downstreamHost/api/policies/${URLEncoder.encode(vhost, "utf-8")}/${URLEncoder.encode(upstreamName, "utf-8")}"

    val data =
      s"""{
         |  "pattern":"${pattern.replace("\\", "\\\\")}",
         |  "definition": {
         |    "federation-upstream": "$upstreamName"
         |  },
         |  "apply-to": "$applyTo"
         |}""".stripMargin

    isExistingPolicy(downstreamHost, downstreamUser, downstreamPass, vhost, upstreamName) || put(uri, data, downstreamUser, downstreamPass)
  }

  private def get(uri: String, user: String, password: String): Boolean = {
    var connection = new URL(uri).openConnection(java.net.Proxy.NO_PROXY).asInstanceOf[HttpURLConnection]

    try {
      connection.setRequestMethod("GET")
      connection.setRequestProperty("Accept", "application/json")
      connection.setRequestProperty("Authorization", s"Basic ${Base64.getEncoder.encodeToString(s"$user:$password".getBytes("utf-8"))}")
      connection.setUseCaches(false)

      connection.getResponseCode match {
        case 200 => {
          var ir = new BufferedReader(new InputStreamReader(connection.getInputStream))
          var line = ir.readLine()

          while (line != null) {
            line = ir.readLine() // Iterate through this so we can get a result value.
          }

          ir.close()

          true
        }
        case _ => {
          false
        }
      }
    }
    finally {
      if (connection != null) {
        connection.disconnect()
      }
    }
  }

  private def put(uri: String, data: String, user: String, password: String) = {
    var connection = new URL(uri).openConnection(java.net.Proxy.NO_PROXY).asInstanceOf[HttpURLConnection]
    connection.setRequestMethod("PUT")
    connection.setRequestProperty("Content-Type", "application/json")
    connection.setRequestProperty("Content-Length", data.getBytes.length.toString)
    connection.setRequestProperty("Authorization", s"Basic ${Base64.getEncoder.encodeToString(s"$user:$password".getBytes("utf-8"))}")
    connection.setUseCaches(false)
    connection.setDoOutput(true)

    var wr = new DataOutputStream(connection.getOutputStream)
    wr.writeBytes(data)
    wr.close()

    var ir = new BufferedReader(new InputStreamReader(connection.getInputStream))
    var line = ir.readLine()

    while (line != null) {
      line = ir.readLine() // Iterate through this so we can get a result value.
    }

    ir.close()

    if (connection != null) {
      connection.disconnect()
    }

    true
  }
}
