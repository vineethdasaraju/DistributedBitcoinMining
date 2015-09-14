import akka.actor._
import java.security.MessageDigest
import scala.util.Random
import java.util.Calendar

object ClientServerBitcoinMiner {

  sealed trait DosMessage
  case object Calculate extends DosMessage
  case class Work(start: Int, nrOfElements: Int, nrOfZeroes: Int) extends DosMessage
  case class Result(value: Array[String]) extends DosMessage
  case class Message(value: Array[Int]) extends DosMessage
  case class ResultDisplay(res: Array[String])

  def main(args: Array[String]){
    var zeroes: Int = 0
    var client: Int = 0
    if(args.length > 0){
      if(args(0).contains('.')){
        client = 1
      }
      else{
        zeroes = args(0).toInt
      }
    }
    else{
      zeroes = 3
    }
    if(client == 1){
      implicit val system = ActorSystem("ClientSystem")
      val clientActor = system.actorOf(Props(new ClientActor(args(0))), name = "ClientActor")
      clientActor ! "START"
    }
    else{
      calculate(client = 0, nrOfWorkers = 4, nrOfElements = 10000000, nrOfMessages = 4, nrOfZeroes = zeroes)
    }
  }

  class ClientActor(addr: String) extends Actor {
    //create the actor for the server
    var addrFull: String = "akka.tcp://ServerSystem@"+addr+":6140/user/ServerActor"
    var server = context.actorFor(addrFull)
    var nrOfResults: Int = 0
    var resultCount: Int = 0
    var s: Int = 0
    var ind: Int = 0
    var outarr: Array[String] = new Array[String](100000)
    var value2: Array[Int] = new Array[Int](4)

    def receive = {
      case "START" =>
        server ! "Identify me"
      case Message(value) =>
        //use the string and calculate the bitcoins
        println("Got reply from server");
        value2 = value
        val workerRouter = context.actorOf(
          Props[Worker].withRouter(akka.routing.RoundRobinRouter(value(0))), name = "workerRouter")
        var ind = 0
        while(ind <= value(2)){
          workerRouter ! Work(ind * value(1), value(1), value(3))
          ind += 1
        }
        server = sender
      case Result(value) =>
        s = 0
        resultCount += 1
        //println("Hello")
        //println(nrOfResults)
        while(value(s) != null){
          outarr(nrOfResults) = value(s)
          s = s + 1
          nrOfResults += 1
          println(nrOfResults + "\t" + value(s-1)+"\t"+ Calendar.getInstance().getTime())
        }
        if (resultCount == value2(2)) {
          outarr(nrOfResults) = null
          // Send the result to the listener
          println(server)
          println("Completed the work")
          server ! Result(outarr)
          //, duration = (System.currentTimeMillis - start).millis)
          // Stops this actor and all its supervised children
          context.stop(self)
          //context.system.shutdown()
        }
    }
  }

  class ServerActor(client: Int, nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int, nrOfZeroes: Int, listener: ActorRef) extends Actor {
    //This is also the master actor
    var nrOfResults: Int = 0
    var resultCount: Int = 0
    var clientMessages: Int = 0
    var s: Int = 0
    var ind: Int = 0
    var outarr: Array[String] = new Array[String](100000)
    var nrOfMessages2 = nrOfMessages
    //val start: Long = System.currentTimeMillis

    val workerRouter = context.actorOf(
      Props[Worker].withRouter(akka.routing.RoundRobinRouter(nrOfWorkers)), name = "workerRouter")
    def receive = {
      case "Identify me" =>
        //use this client to mine bitcoins
        val sendingItem: Array[Int] = new Array[Int](4)
        sendingItem(0) = 4
        sendingItem(1) = 10000
        //println(nrOfMessages2+"  "+ind)
        //sendingItem(2) = (nrOfMessages2 - ind)/2
        //nrOfMessages2 = nrOfMessages2 - sendingItem(2)
        sendingItem(2) = 4000
        println(sendingItem(2))
        sendingItem(3) = nrOfZeroes
        if(!sender.path.toString().contains("dead") && sendingItem(2)!=0){
          clientMessages += 1
          sender ! Message(sendingItem)
        }

      case Calculate =>
        while(ind <= nrOfMessages2){
          workerRouter ! Work(ind * nrOfElements, nrOfElements, nrOfZeroes)
          ind += 1
        }
      case Result(value) =>
        s = 0
        resultCount += 1
        //println("Hello")
        //println(nrOfResults)
        while(value(s) != null){
          outarr(nrOfResults) = value(s)
          s = s + 1
          nrOfResults += 1
          println(nrOfResults + "\t" + value(s-1)+ "\t"+ Calendar.getInstance().getTime())
        }
        if (resultCount == nrOfMessages2 + clientMessages) {
          outarr(nrOfResults) = null
          // Send the result to the listener
          listener ! ResultDisplay(outarr)
          //, duration = (System.currentTimeMillis - start).millis)
          // Stops this actor and all its supervised children
          context.stop(self)
        }

    }
  }

  class Listener extends Actor {
    var d: Int = 0
    def receive = {
      case ResultDisplay(res) =>
        if(res(d) != null){
          println("Execution Completed. The values are: ")
        }
        while(res(d) != null){
          val sha = MessageDigest.getInstance("SHA-256");
          val hash = sha.digest(res(d).getBytes).foldLeft("")((s: String, b: Byte) => s +
            Character.forDigit((b & 0xf0) >> 4, 16) +
            Character.forDigit(b & 0x0f, 16));
          val m : String = hash.toString()
          println(res(d) +"\t" + m)
          d += 1
        }
        //println("\n\tapproximation: \t\t%s\n\tCalculation time: \t"
        //  .format(pi))
        context.system.shutdown()
    }
  }

  def calculate(client: Int, nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int, nrOfZeroes: Int){
    val serversystem = ActorSystem("ServerSystem")
    // create the result listener, which will print the result and shutdown the system
    val listener = serversystem.actorOf(Props[Listener], name = "listener")
    val serverActor = serversystem.actorOf(Props(new ServerActor(client, nrOfWorkers, nrOfElements, nrOfMessages, nrOfZeroes, listener)), name = "ServerActor")
    serverActor ! Calculate
  }

  class Worker extends Actor {

    def calculateBitCoinFor(start: Int, nrOfElements: Int, nrOfZeroes: Int): Array[String] = {
      val resarr: Array[String] = new Array[String](1000)
      var k: Int = 0
      val sha = MessageDigest.getInstance("SHA-256");
      var zeroValue = ""
      for(x <- 1 to nrOfZeroes){
        zeroValue += "0"
      }
      for(i <- start until (start + nrOfElements)){
        //val num = 100
        var returnValue = ""
        val a = Random.nextInt(10) + 1
        for(j <- 1 to a){
          returnValue = returnValue + Random.nextPrintableChar()
        }
        val text = "vineethd" + returnValue

        val hash = sha.digest(text.getBytes).foldLeft("")((s: String, b: Byte) => s +
          Character.forDigit((b & 0xf0) >> 4, 16) +
          Character.forDigit(b & 0x0f, 16));
        val m : String = hash.toString()
        //println(i + "  ")
        if(m.startsWith(zeroValue)){
          //println("Actor: "+(i)+" ASCII: "+returnValue+" Message: "+m)
          resarr(k) = "vineethd" + returnValue
          k = k + 1
        }
        if(i == start + nrOfElements-1){
          //println("Actor: "+(i))
          resarr(k) = null
        }
      }
      resarr
    }

    def receive = {
      case Work(start, nrOfElements, nrOfZeroes) =>
        sender ! Result(calculateBitCoinFor(start, nrOfElements, nrOfZeroes)) // perform the work
    }
  }
}