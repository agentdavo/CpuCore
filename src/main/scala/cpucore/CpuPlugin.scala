package cpucore

import spinal.core._
import spinal.lib._
import spinal.core.sim._

import spinal.lib.pipeline.Connection.{DIRECT, M2S}
import spinal.lib.pipeline.{Connection, Pipeline, Stageable, StageableOffset}
import spinal.lib.bus.bmb.{Bmb, BmbParameter}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait CpuPlugin {

  def setup(core: CpuCore): Unit
  def inFetch(fetch: FetchStage): Unit
  def inDecode(decode: DecodeStage): Unit
  def inExecute(execute: ExecuteStage): Unit
  def inMemory(memory: MemoryStage): Unit
  def inWriteBack(writeback: WriteBackStage): Unit

  // Add a method to provide a custom description of the plugin
  def getDescription: String = this.getClass.getSimpleName
  
}