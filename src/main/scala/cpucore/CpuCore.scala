package cpucore

import spinal.core._
import spinal.lib._
import spinal.core.sim._
import spinal.lib.sim._

import spinal.lib.bus.bmb.{Bmb, BmbParameter}
import spinal.lib.pipeline.Pipeline
import spinal.lib.pipeline.Stage
import spinal.lib.pipeline.Connection.{DIRECT, M2S}
import spinal.lib.pipeline.ConnectionLogic

import spinal.lib.bus.misc.SizeMapping
import scala.util.Random

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

//
// data bundles at each stage
//

case class Instruction() extends Bundle {
  val opcode = Bits(7 bits)
}

case class DecodedInstruction() extends Bundle {
  val opcode = Bits(7 bits)
}

case class ExecutedInstruction() extends Bundle {
  val opcode = Bits(7 bits)
}

case class MemInstruction() extends Bundle {
  val opcode = Bits(7 bits)
}

//
// components at each stage
//

class FetchStage extends Component {
  val io = new Bundle {
    val output = Flow(Instruction())
  }
}

class DecodeStage extends Component {
  val io = new Bundle {
    val input = Flow(Instruction())
    val output = Flow(DecodedInstruction())
  }
}

class ExecuteStage extends Component {
  val io = new Bundle {
    val input = Flow(DecodedInstruction())
    val output = Flow(ExecutedInstruction())
  }
}

class MemoryStage extends Component {
  val io = new Bundle {
    val input = Flow(ExecutedInstruction())
    val output = Flow(MemInstruction())
  }
}

class WriteBackStage extends Component {
  val io = new Bundle {
    val input = Flow(MemInstruction())
  }
}


//
// CpuCore
//

class CpuCore(
    plugins: Seq[CpuPlugin],
    iBusParams: BmbParameter,
    dBusParams: BmbParameter
) extends Component {

  val io = new Bundle {
    val iBus = slave(Bmb(iBusParams))
    val dBus = master(Bmb(dBusParams))
  }

  // Create components for each stage
  val fetch = new FetchStage
  val decode = new DecodeStage
  val execute = new ExecuteStage
  val memory = new MemoryStage
  val writeback = new WriteBackStage

  val pipeline = new Pipeline {

    // Create stages from the components

    val fetchStage = new Stage(fetch)
    val decodeStage = new Stage(decode)
    val executeStage = new Stage(execute)
    val memoryStage = new Stage(memory)
    val writebackStage = new Stage(writeback)

    // Connect the stages using the Pipeline.connect method

    connect(fetchStage, decodeStage)(M2S())
    connect(decodeStage, executeStage)(M2S())
    connect(executeStage, memoryStage)(M2S())
    connect(memoryStage, writebackStage)(M2S())
  }

  pipeline.build()
  pipeline.setCompositeName(this)

  // Apply plugins
  plugins.foreach { plugin =>
    plugin.setup(this)
    plugin.inFetch(fetch)
    plugin.inDecode(decode)
    plugin.inExecute(execute)
    plugin.inMemory(memory)
    plugin.inWriteBack(writeback)
  }

  // Connect pipeline head to iBus
  val iBusCmdPipe = pipeline.head.input.toBmb()
  io.iBus.cmd << iBusCmdPipe.cmd
  iBusCmdPipe.rsp << io.iBus.rsp

  // Connect pipeline tail to dBus
  val dBusRspPipe = pipeline.tail.output.toBmb()
  io.dBus.rsp << dBusRspPipe.rsp
  dBusRspPipe.cmd << io.dBus.cmd

}


object CpuCoreSim {

  def main(args: Array[String]): Unit = {
  
    // Set up BMB parameters and size mapping
	
	  val iBusParams = BmbParameter(
		  addressWidth = 32,
		  dataWidth = 64,
		  sourceWidth = 0,
		  contextWidth = 0,
		  lengthWidth = 0,
		  alignment = BmbParameter.BurstAlignement.LENGTH,
		  alignmentMin = 0,
		  accessLatencyMin = 1,
		  canRead = true,
		  canWrite = false,
		  canExclusive = false,
		  maximumPendingTransaction = 8
	  )
	  
	  
	  
	  val dBusParams = BmbParameter(
		  addressWidth = 32,
		  dataWidth = 64,
		  sourceWidth = 0,
		  contextWidth = 0,
		  lengthWidth = 0,
		  alignment = BmbParameter.BurstAlignement.LENGTH,
		  alignmentMin = 0,
		  accessLatencyMin = 1,
		  canRead = true,
		  canWrite = true,
		  canExclusive = false,
		  maximumPendingTransaction = 8
	  )
	
    val bmbSize = 1 << 20
    val bmbMapping = SizeMapping(0, bmbSize)

    SimConfig.withWave.compile(new CpuCore(Seq.empty, iBusParams, dBusParams)).doSimUntilVoid { dut =>
	
      val memory = BmbMemory(dBusParams)
	  
      memory.setMapping(bmbMapping)
      dut.io.iBus << memory.io.bmb
      dut.io.dBus >> memory.io.bmb

      // Generate random instructions and write them to memory
	  
      val instructions = Array.tabulate(128) { _ =>
        val instruction = Random.nextInt(1 << 32)
        memory.write(instruction, Random.nextInt(bmbSize))
        instruction
      }

      // Start execution at address 0
	  
      dut.clockDomain.forkStimulus(10)
      dut.clockDomain.waitSampling()

      dut.io.iBus.cmd.valid #= false
      dut.io.iBus.cmd.payload.pc #= 0
      dut.io.iBus.cmd.payload.prot.asMasters.foreach(_ #= false)
      dut.io.iBus.cmd.payload.len #= 0
      dut.io.iBus.cmd.payload.last #= true
      dut.io.iBus.cmd.payload.setWrite()
      dut.io.iBus.cmd.ready #= true
      dut.clockDomain.waitSampling()

      // Read back results from writeback stage
	  
      val results = ArrayBuffer[Int]()
	  
      while (results.length < instructions.length) {
        dut.clockDomain.waitSampling()
        while (dut.pipeline.tail.output.valid.toBoolean) {
          results += dut.pipeline.tail.output.payload.opcode.toInt
          dut.pipeline.tail.output.ready #= true
          dut.clockDomain.waitSampling()
        }
      }

      // Compare results to expected values
	  
      assert(results == instructions.map(Instruction(_).opcode.toInt))
	  
    }
  }
}
