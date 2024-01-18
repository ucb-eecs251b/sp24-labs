package srambist

import chisel3._
import chisel3.util.log2Ceil

/** Access patterns that can be exercised by the BIST.
  *
  *   W0 - Write `data0` to each address sequentially, starting from address 0.
  *   W1 - Write `data1` to each address sequentially, starting from address 0.
  *   R0 - Reads each address sequentially and validates that it equals `data0`, 
  *        starting from address 0.
  *   R1 - Reads each address sequentially and validates that it equals `data1`, 
  *        starting from address 0.
  */
object Pattern extends Enumeration {
  type Type = Value
  val W0, W1, R0, R1 = Value
}

/** Runs a set of patterns against an SRAM to verify its behavior.
  *
  * Once the BIST is complete, `done` should be set high and remain high. If the BIST
  * failed, `fail` should be asserted on the same cycle and remain high.
  */
class SramBist(numWords: Int, dataWidth: Int, patterns: Seq[Pattern.Type])
    extends Module {
  val io = IO(new Bundle {
    // SRAM interface
    val we = Output(Bool())
    val addr = Output(UInt(log2Ceil(numWords).W))
    val din = Output(UInt(dataWidth.W))
    val dout = Input(UInt(dataWidth.W))

    // BIST interface
    val data0 = Input(UInt(dataWidth.W))
    val data1 = Input(UInt(dataWidth.W))
    val done = Output(Bool())
    val fail = Output(Bool())
  })

  ???

}
