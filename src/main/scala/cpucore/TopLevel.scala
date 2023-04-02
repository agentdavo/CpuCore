package cpucore

import spinal.core._
import spinal.lib._
import spinal.core.sim._

import spinal.lib.pipeline.Connection.{DIRECT, M2S}
import spinal.lib.pipeline.{Connection, Pipeline, Stageable, StageableOffset}
import spinal.lib.bus.bmb.{Bmb, BmbParameter, BmbDecoder}
import spinal.lib.bus.misc.AddressMapping
import spinal.lib.bus.simple._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class TopLevel(coreClockDomain: ClockDomain = ClockDomain.current) extends Component {

  val io = new Bundle {
    val mainBus = master(Bmb(dBusParams))
  }

  val plugins = ArrayBuffer[CpuPlugin]()
    plugins ++= List(
	  new MulPlugin()
  )

  val cpuCore = new CpuCore( plugins: Seq[CpuPlugin],
                             iBusParams: BmbParameter,
                             dBusParams: BmbParameter )
  
  val coreArea = new ClockingArea(coreClockDomain) {
    val iBusConnection = connectSimpleIBusToBmb(cpuCore.fetch.io.cmd, cpuCore.fetch.io.rsp)
    val dBusConnection = connectSimpleDBusToBmb(cpuCore.writeback.io.cmd, cpuCore.writeback.io.rsp)
    iBusConnection <> io.mainBus
    dBusConnection <> io.mainBus
  }
  
    // The context signal is used to match requests to responses in pipelined bus protocols. For the instruction bus, 
	// we don't expect any responses so we can set the context signal to False to indicate that no response is expected.
	
	def connectSimpleIBusToBmb(cmd: IBusSimpleCmd, rsp: IBusSimpleRsp): Bmb = {
	  val bus = Bmb(iBusParams)
	  cmd.ready := bus.cmd.ready
	  bus.cmd.valid := cmd.valid
	  bus.cmd.opcode := Bmb.Cmd.Opcode.READ
	  bus.cmd.address := cmd.pc.resized
	  bus.cmd.length := 3
	  bus.cmd.last := True
	  bus.cmd.context := False
	  rsp.valid := bus.rsp.valid
	  rsp.inst := bus.rsp.data
	  rsp.error := bus.rsp.isError
	  bus.rsp.ready := True
	  bus
	}
	
	// For the data bus, we expect a response with the data we read or wrote. However, we don't need to use the context 
	// signal to match requests to responses because we are only issuing one request at a time. So we can set the mask 
	// to "000" to indicate that we don't care about the context signal. This simplifies the configuration of the BMB command.


	def connectSimpleDBusToBmb(cmd: DBusSimpleCmd, rsp: DBusSimpleRsp): Bmb = {
	  val bus = Bmb(dBusParams)
	  cmd.ready := bus.cmd.ready
	  bus.cmd.valid := cmd.valid
	  bus.cmd.opcode := (Bool(cmd.wr) ? Bmb.Cmd.Opcode.WRITE | Bmb.Cmd.Opcode.READ)
	  bus.cmd.address := cmd.address.resized
	  bus.cmd.data := cmd.data
	  bus.cmd.length := (cmd.size.mux(0 -> B"00", 1 -> B"01", default -> B"11") asBits)
	  bus.cmd.mask := "000".asBits
	  bus.cmd.context := False
	  rsp.valid := bus.rsp.valid && !bus.rsp.context
	  rsp.data := bus.rsp.data
	  rsp.error := bus.rsp.isError
	  bus.rsp.ready := True
	  bus
	}

	// Utility function to generate a mask from a given size
	def genMask(cmd : DBusSimpleCmd) = {
      cmd.size.mux(
		U(0) -> B"0001",
		U(1) -> B"0011",
		default -> B"1111"
	  ) |<< cmd.address(1 downto 0)
	}

}


object TopLevelVerilog {
  def main(args: Array[String]): Unit = {
   SpinalConfig(targetDirectory = "output").generateVerilog(new TopLevel())
  }
}
