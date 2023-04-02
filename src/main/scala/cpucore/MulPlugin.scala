package cpucore

import spinal.core._
import spinal.lib._
import spinal.core.sim._

import spinal.lib.pipeline.Connection.{DIRECT, M2S}
import spinal.lib.pipeline.{Connection, Pipeline, Stageable, StageableOffset}
import spinal.lib.bus.bmb.{Bmb, BmbParameter}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class MulPlugin extends CpuPlugin {

  override def setup(core: CpuCore): Unit = {
    // Perform custom setup actions, such as adding signals to the core
  }
  
  override def inFetch(fetch: FetchStage): Unit = {
    // Implement custom behavior for the fetch stage
  }

  override def inDecode(decode: DecodeStage): Unit = {
    // Implement custom behavior for the decode stage
  }

  override def inExecute(execute: ExecuteStage): Unit = {
    // Implement custom behavior for the execute stage
  }

  override def inMemory(memory: MemoryStage): Unit = {
    // Implement custom behavior for the memory stage
  }

  override def inWriteBack(writeback: WriteBackStage): Unit = {
    // Implement custom behavior for the writeback stage
  }

  // Provide a custom description of the plugin
  override def getDescription: String = {
    "Multiplier CPU Plugin"
  }

}
