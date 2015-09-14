import akka.actor._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import java.security.MessageDigest
import akka.actor.ActorSystem
import java.net.InetAddress
import scala.util.Random

object BCMiner extends App {

  case class StartMining()
  case class StartClientMining(target: Int, inputRange: Int)
  case class StartServer()
  case class Begin()
  case class ClientMiner()
  case class ContactServer()
  case class End()
  case class Bitcoin(str: String, hash: String)
  case class Result(nrOfResults: Int, duration: Duration)

  class Client(ip: String, nrOfWorkers: Int) extends Actor {
    var nOfReturns: Int = _
    var nOfResults: Int = _
    val start: Long = System.currentTimeMillis
    val remote = context.actorFor("akka.tcp://MasterMiningSystem@" + ip + ":5150/user/master")

    def receive = {
      case ContactServer() =>
        remote ! ClientMiner()

      case StartClientMining(target, inputRange) =>
        for (i <- 0 until nrOfWorkers)
          context.actorOf(Props(new Worker(target, inputRange/nrOfWorkers))) ! Begin()
        println("Bitcoin mining started.")

      case Bitcoin(str, hash) =>
        nOfResults += 1
        println("Bitcoin found and sent to server.")
        remote ! Bitcoin(str, hash)

      case End() =>
        nOfReturns += 1
        if(nOfReturns == nrOfWorkers) {
          val duration = (System.currentTimeMillis - start).millis
          remote ! End()
          println("\n\tNumber of bitcoins: \t\t%s\n\tCalculation time: \t%s"
            .format(nOfResults, duration))
          println("Bitcoing mining done. Shutting down client...")
          context.system.shutdown()
        }

      case message: String =>
        println(s"Received the following message: '$message'")
    }
  }

  class Worker(target: Int, input: Int) extends Actor {

    private val sha = MessageDigest.getInstance("SHA-256")

    def hex_digest(s: String): String = {
      sha.digest(s.getBytes)
        .foldLeft("")((s: String, b: Byte) => s +
        Character.forDigit((b & 0xf0) >> 4, 16) +
        Character.forDigit(b & 0x0f, 16))
    }

    def receive = {
      case Begin() =>
        var continue = true
        var trails = 0
        while (continue) {
          val str = getNewString()
          val hash = hex_digest(str.toString)
          if(isBitcoin(hash,target)) {
            sender ! Bitcoin(str, hash)
            //continue = false
          }
          trails += 1
          if (trails == input) {
            continue = false
            sender ! End()
            context.stop(self)
          }
        }
    }
  }

  class Master(nrOfWorkers: Int, target: Int)
    extends Actor {

    var pi: Double = _
    var nOfResults: Int = _
    var nOfReturns: Int = _
    var nOfAssignments:Int = _
    val start: Long = System.currentTimeMillis
    val inputRange = 100000000

    def receive = {
      case ClientMiner() =>
        sender ! StartClientMining(target, inputRange)
        nOfAssignments += 1

      case StartMining() =>
        for (i <- 0 until nrOfWorkers) {
          context.actorOf(Props(new Worker(target, inputRange/nrOfWorkers))) ! Begin()
          nOfAssignments += 1
        }

      case Bitcoin(str, hash) =>
        nOfResults += 1
        println("%s\t%s".format(str,hash))

      case End() =>
        nOfReturns += 1
        if(nOfReturns == nOfAssignments) {
          // Calculate the program runtime
          val duration = (System.currentTimeMillis - start).millis
          println("\n\tNumber of bitcoins: \t\t%s\n\tCalculation time: \t%s"
            .format(nOfResults, duration))
          println("Shutting down master...")
          context.system.shutdown()
        }
    }

  }

  def getNewString(): String = {
    val len = Random.nextInt(15)+1
    val str = new StringBuilder()
    str.append("smaddula")
    for(i <- 1 until len+1) {
      str.append((Random.nextInt(94)+33).toChar)
    }
    return str.toString
  }

  def isBitcoin(str:String,target:Int): Boolean = {
    val pattern = new StringBuilder()
    for(i <- 1 until target+1) {
      pattern.append('0')
    }
    val result = str.startsWith(pattern.toString)
    return result
  }

  override def main(args: Array[String]) {

    val hostAddr = InetAddress.getLocalHost.getHostAddress()
    val serverConfig = ConfigFactory.parseString(
      """akka{
           actor{
             provider = "akka.remote.RemoteActorRefProvider"
    	   }
    	   remote{
             enabled-transports = ["akka.remote.netty.tcp"]
             netty.tcp{
    		   hostname = """ + hostAddr + """
    		   port = 5150
    	     }
           }
      }""")

    val clientConfig = ConfigFactory.parseString(
      """akka{
		   actor{
		  	 provider = "akka.remote.RemoteActorRefProvider"
		   }
		   remote{
             enabled-transports = ["akka.remote.netty.tcp"]
		  	 netty.tcp{
			   hostname = "127.0.0.1"
			   port = 0
			 }
		   }
      }""")

    val nrOfWorkers = 4

    if (!args(0).isEmpty()) {
      if (args(0).contains('.')) {
        val system = ActorSystem("ClientMiningSystem", ConfigFactory.load(clientConfig))

        // Create client actor
        val client = system.actorOf(Props(new Client(args(0), nrOfWorkers)), name = "client")
        client ! ContactServer()
      }
      else {
        val system = ActorSystem("MasterMiningSystem", ConfigFactory.load(serverConfig))

        // create master
        val master = system.actorOf(Props(new Master(nrOfWorkers, args(0).toInt)), name = "master")
        master ! StartMining()
      }
    }

  }
}
