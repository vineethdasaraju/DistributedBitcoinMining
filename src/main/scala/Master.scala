import java.security.MessageDigest
import akka.actor.{ActorRef, Props, Actor}
import scala.concurrent.duration.Duration
import akka.actor._
import akka.routing.RoundRobinRouter

object BitcoinMiner extends App {
  calculate(nrOfWorkers = 4, nrOfZeroes = 3)

  sealed trait BitcoinMessage
  case object Calculate extends BitcoinMessage
  case class Work(start: Double, nrOfZeroes: Int) extends BitcoinMessage
  case class Result(bitcoin: String) extends BitcoinMessage
  case class DisplayBitcoin(bitcoin: String, duration: Duration) extends BitcoinMessage

  def calculate(nrOfWorkers: Int, nrOfZeroes: Int) {
    val system = ActorSystem("BitcoinSystem")
    val listener = system.actorOf(Props[Listener], name = "listener")
    val master = system.actorOf(Props(new Master(nrOfWorkers, nrOfZeroes, listener)), name = "master")
    master ! Calculate
  }

  class Master(nrOfWorkers: Int, nrOfZeroes: Int, listener: ActorRef) extends Actor {

    var bitcoin: String = _
    val start: Long = System.currentTimeMillis
    val workerRouter = context.actorOf(Props[Worker].withRouter(RoundRobinRouter(nrOfWorkers)), name = "workerRouter")

    def receive = {
      case Calculate =>
        for (i ← 0 until nrOfWorkers) workerRouter ! Work(start, nrOfZeroes)
      case Result(bitcoin) =>
        listener ! DisplayBitcoin(bitcoin, duration = Duration(System.currentTimeMillis - start, "millis"))
        // context.stop(self)
    }
  }

  class Worker extends Actor {

    def startMineBitcoin(start: Double, nrOfZeroes: Int): String = {
      // Generate random string
      // Use SHA 256 to see hashed string
      var bitcoin =""
      var str = ""
      while(!satisfyCondition(nrOfZeroes, bitcoin)) {
        str = generateRandomString
        bitcoin = getSHA256Hash(str)
      }
      str + ": " + bitcoin
    }

    def randomStringFromCharList(length: Int, chars: Seq[Char]): String = {
      val sb = new StringBuilder
      for (i <- 1 to length) {
        val randomNum = util.Random.nextInt(chars.length)
        sb.append(chars(randomNum))
      }
      sb.toString
    }

    def randomAlphaNumericString(length: Int): String = {
      val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
      randomStringFromCharList(length, chars)
    }

    def generateRandomString : String = {
      "smaddula" + randomAlphaNumericString(util.Random.nextInt(10))
    }

    def satisfyCondition(nrOfZeroes: Int, str: String): Boolean = {
      var zeroesString = ""
      for(l <-1 to nrOfZeroes){
        zeroesString +="0"
      }
      if(str.startsWith(zeroesString)) true
      else false;
    }

    def getSHA256Hash(value: String): String = {
      val result: String = MessageDigest.getInstance("SHA-256").digest(value.getBytes()).foldLeft("")((s: String, b: Byte) => s + Character.forDigit((b & 0xf0) >> 4, 16) + Character.forDigit(b & 0x0f, 16))
      result
    }

    def receive = {
      case Work(start, nrOfZeroes) =>
        sender ! Result(startMineBitcoin(start, nrOfZeroes)) // perform the work
    }
  }

  class Listener extends Actor {
    def receive = {
      case DisplayBitcoin(bitcoin, duration) =>
        println("Bitcoin " + bitcoin + " found in " + duration + "seconds.")
//        context.system.shutdown()
    }
  }
}