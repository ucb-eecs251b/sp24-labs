package srambist

import chisel3._
import chisel3.util.log2Ceil
import chiseltest._
import org.scalatest.freespec.AnyFreeSpec

object FailureMode extends Enumeration {
  type Type = Value
  val None, StuckAt0, Bound, Transition = Value
}

class Sram(numWords: Int, dataWidth: Int, failureMode: FailureMode.Type)
    extends Module {
  val io = IO(new Bundle {
    val we = Input(Bool())
    val addr = Input(UInt(log2Ceil(numWords).W))
    val din = Input(UInt(dataWidth.W))
    val dout = Output(UInt(dataWidth.W))
  })

  val mem = Mem(
    numWords,
    UInt(dataWidth.W)
  )
  val dout = Reg(UInt(dataWidth.W))

  io.dout := dout
  dout := DontCare

  val rdwrPort = mem(io.addr)
  when(io.we) {
    failureMode match {
      case FailureMode.None     => rdwrPort := io.din
      case FailureMode.StuckAt0 => rdwrPort := rdwrPort & io.din
      case FailureMode.Bound => {
        when(io.addr===31.U) {
          rdwrPort := 0.U
        }.otherwise {
          rdwrPort := io.din
        }
      }
      case FailureMode.Transition => {
        when(
          io.addr === "hFD".U && io.din === "hDEADBEEF".U && rdwrPort === "hCAFEF00D".U
        ) {
          rdwrPort := 0.U
        }.otherwise {
          rdwrPort := io.din
        }
      }
    }
  }.otherwise {
    dout := rdwrPort
  }
}

class SramBistTest(
    numWords: Int,
    dataWidth: Int,
    patterns: Seq[Pattern.Type],
    failureMode: FailureMode.Type
) extends Module {
  val io = IO(new Bundle {
    // BIST interface
    val data0 = Input(UInt(dataWidth.W))
    val data1 = Input(UInt(dataWidth.W))
    val done = Output(Bool())
    val fail = Output(Bool())
  })
  val bist = Module(new SramBist(numWords, dataWidth, patterns))
  val sram = Module(new Sram(numWords, dataWidth, failureMode))
  sram.io.we := bist.io.we
  sram.io.addr := bist.io.addr
  sram.io.din := bist.io.din
  bist.io.dout := sram.io.dout

  bist.io.data0 := io.data0
  bist.io.data1 := io.data1
  io.done := bist.io.done
  io.fail := bist.io.fail
}

/** This is a trivial example of how to run this Specification From within sbt
  * use:
  * {{{
  * testOnly srambist.SramBistSpec
  * }}}
  * From a terminal shell use:
  * {{{
  * sbt 'testOnly srambist.SramBistSpec'
  * }}}
  */
class SramBistSpec extends AnyFreeSpec with ChiselScalatestTester {

  "Standard SRAM behavioral model should pass BIST" in {
    test(
      new SramBistTest(
        64,
        32,
        Seq(Pattern.W1, Pattern.R1, Pattern.W0, Pattern.R0),
        FailureMode.None
      )
    ).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.io.data0.poke(0.U(32.W))
      dut.io.data1.poke("hFFFFFFFF".U(32.W))

      while (!dut.io.done.peek().litToBoolean) {
        dut.clock.step()
      }

      dut.io.fail.expect(false.B)
    }
  }

  "Writing zeros to stuck-at-0 SRAM should pass BIST" in {
    test(
      new SramBistTest(
        256,
        32,
        Seq(Pattern.W0, Pattern.W1, Pattern.R1),
        FailureMode.StuckAt0
      )
    ).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.io.data0.poke("hFFFFFFFF".U(32.W))
      dut.io.data1.poke(0.U(32.W))

      while (!dut.io.done.peek().litToBoolean) {
        dut.clock.step()
      }

      dut.io.fail.expect(false.B)
    }
  }

  "Writing all 0s and all 1s to transition SRAM should pass BIST" in {
    test(
      new SramBistTest(
        128,
        64,
        Seq(Pattern.W0, Pattern.R0, Pattern.W1, Pattern.R1),
        FailureMode.Transition
      )
    ).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.io.data0.poke("hFFFFFFFFFFFFFFFF".U(64.W))
      dut.io.data1.poke(0.U(64.W))

      while (!dut.io.done.peek().litToBoolean) {
        dut.clock.step()
      }

      dut.io.fail.expect(false.B)
    }
  }

  "BIST should verify last address" in {
    test(
      new SramBistTest(
        32,
        64,
        Seq(Pattern.W0, Pattern.R0),
        FailureMode.Bound
      )
    ).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.io.data0.poke("hFFFFFFFFFFFFFFFF".U(64.W))

      while (!dut.io.done.peek().litToBoolean) {
        dut.clock.step()
      }

      dut.io.fail.expect(true.B)
    }
  }


  "Writing certain data to transition SRAM should fail BIST" in {
    test(
      new SramBistTest(
        256,
        32,
        Seq(Pattern.W0, Pattern.R0, Pattern.W1, Pattern.R1),
        FailureMode.Transition
      )
    ).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      dut.clock.setTimeout(2000)
      dut.io.data0.poke("hCAFEF00D".U(32.W))
      dut.io.data1.poke("hDEADBEEF".U(32.W))

      while (!dut.io.done.peek().litToBoolean) {
        dut.clock.step()
      }

      dut.io.fail.expect(true.B)
    }
  }

  "Stuck-at-0 SRAM should fail BIST" in {
    test(

      ???

    ).withAnnotations(Seq(WriteVcdAnnotation)) { dut: SramBistTest =>

      ???

    }
  }
}
