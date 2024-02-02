# Lab 2: Chipyard

In this lab, we will integrate the SRAM BIST we designed in [Lab 1](../lab1) into UC Berkeley's [Chipyard](https://github.com/ucb-bar/chipyard) framework.

This lab will only cover a small subset of Chipyards features, so feel free to explore its [documentation](https://chipyard.readthedocs.io/en/latest/) to learn more.

## Getting started

The steps below assume you completed the instructional machine setup outlined in [Lab 1](../lab1/README.md). If you have not done so already, ensure that you have created an instructional account and set up a GitHub SSH key on the instructional machines.

### Chipyard

We will now set up your Chipyard environment. First, accept the GitHub classroom assignment using the link provided on Ed. Once your personal repo is created, copy the SSH clone URL.

For this course, you must work in the `/scratch` directory on a lab machine of your choice. Since `/scratch` is not automatically backed up, you will need to login to the same server each time. Chipyard will generate too much data for it to fit in
your home directory.

> [!WARNING]
> If you run commands in a "raw" (without `nohup` or `tmux`) SSH terminal, they will be killed when you exit your session (or if your wifi goes out for a few seconds).
> To use [`tmux`](https://tmuxcheatsheet.com/), you can add `RemoteCommand tmux new -A -s ssh` to your ssh config for your instructional account or run `tmux new-s -t <name>` once you log in.
> You can also run a command `cmd` as `nohup cmd` to prevent the command from being killed if the session exits, but this is less convenient.
> If you manually created a `tmux` session, you must reattach to it manually the next time you log in with `tmux a -t <name>`

Run the commands below in a `bash` terminal. During the `bash Miniforge3.sh` and `./build-setup.sh` commands, say "yes" and press enter when prompted.

First, create your `/scratch` directory and install `conda`:

```
mkdir -m 0700 -p /scratch/$USER
cd /scratch/$USER
wget -O Miniforge3.sh \
"https://github.com/conda-forge/miniforge/releases/latest/download/Miniforge3-$(uname)-$(uname -m).sh"
bash Miniforge3.sh -p "/scratch/${USER}/conda"
```

Then, setup your repo. This may take about a very long time (potentially over 30 minutes).

```
source ~/.bashrc
unset CONFIG_SHELL
git clone <YOUR SSH CLONE URL> # i.e. git@github.com:ucb-eecs251b/<semester>-chipyard-<username>.git
cd chipyard
git remote add skeleton https://github.com/ucb-eecs251b/<semester>-chipyard.git
git pull skeleton main
mamba install -n base conda-lock=1.4
mamba activate base
git config --global protocol.file.allow always
./build-setup.sh riscv-tools -s 6 -s 7 -s 8 -s 9 -s 10 --force
./scripts/init-vlsi.sh sky130
```

> [!CAUTION]
> `hammer-synopsys-plugins` and `hammer-mentor-plugins` are private repos. PLEASE DO NOT PUBLISH THEM PUBLICLY.

### Environment

To enter your Chipyard environment, you will need to run the following command in each terminal session you open.

```
source /scratch/$USER/chipyard/env.sh
```

> [!TIP]
> If you would like to run this automatically on terminal startup, you can add the command to your `~/.bashrc`
> by running the following:
>
> ```
> echo "source /scratch/$USER/chipyard/env.sh" >> ~/.bashrc
> ```

### Common issues

If you completed the setup successfully, you can skip this section.

#### `build-setup` transaction failed
If you observe that the `conda` install transaction failed, which is often caused by stopping `./build-setup`
prematurely, you will likely need to reinstall conda. To do so, reinstall `conda` by running the following commands:

```bash
cd /scratch/$USER/chipyard
rm -rf .conda-env
cd ..
rm -rf conda
bash Miniforge3.sh -p "/scratch/${USER}/conda"
source ~/.bashrc
```

After running the above commands, go through the repo setup instructions again (you do not need to delete and re-clone the chipyard repo).

#### Hanging on `configure: configuring default subproject`

This might happen if you try to run the installation without `unset`ing the `CONFIG_SHELL` environment variable.
To resolve it, run the following from the Chipyard root directory:

```
unset CONFIG_SHELL
source env.sh
./build-setup.sh riscv-tools -s 1 -s 2 -s 6 -s 7 -s 8 -s 9 -s 10 --force
```

This skips the steps that have already completed prior to the failure and retries the failed step.
Proceed as usual after the command succeeds.

## Integration

To integrate our SRAM BIST into Chipyard, we will create an MMIO peripheral that can be connected to a Rocket core.
This will allow the core to control the BIST simply by writing to certain addresses.

> [!CAUTION]
> The upcoming sections do not give you the exact commands you need to run to do basic things like create a file.
> So, do not skim through looking for commands to run. There is little extraneous information in the instructions,
> so make sure to read them carefully to avoid missing important steps of the process.

> [!TIP]
> You might find `mkdir -p` helpful for creating nested directories.

### Generator

The first step in creating a new peripheral is creating a generator project with the `generators/` folder. To do so, create a new folder for our peripheral at `generators/srambist/`.

Inside of that folder, create a file at `generators/srambist/src/main/scala/SramBist.scala` and copy in the contents of the [`SramBist.scala`](../lab1/srambist/src/main/scala/SramBist.scala) you implemented in Lab 1. To import all of the things we will need for integration, replace your imports at the top of the file with the following:

```scala
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
```

To tell Chipyard that we added this generator, we will need to modify the top level `build.sbt` file. Add the following lines to the bottom.

```scala
lazy val srambist = (project in file("generators/srambist"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(chiselSettings)
  .settings(commonSettings)
```

Then, point to this new project in the Chipyard project's dependencies:

```diff
lazy val chipyard = (project in file("generators/chipyard"))
  .dependsOn(testchipip, rocketchip, boom, hwacha, sifive_blocks, sifive_cache, iocell,
    sha3, // On separate line to allow for cleaner tutorial-setup patches
    dsptools, rocket_dsp_utils,
    gemmini, icenet, tracegen, cva6, nvdla, sodor, ibex, fft_generator,
-   constellation, mempress, barf, shuttle, caliptra_aes)
+   constellation, mempress, barf, shuttle, caliptra_aes, srambist)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(
    libraryDependencies ++= Seq(
      "org.reflections" % "reflections" % "0.10.2"
    )
  )
 .settings(commonSettings)
```

### SRAM macro

We now would like to integrate an SRAM macro that our BIST can test. We already generated all the files needed
to integrate a macro into a digital flow for you in `vlsi/macros/sram22_64x32m4w32_macro/`. The purpose of these files
will be covered in later labs.

In a Chisel project, verilog macros are kept in `src/main/resources/vsrc`. Create this folder at
`generators/srambist/src/main/resources/vsrc/` and `cd` into it. From there, run the following command
to symlink the provided behavioral model of the macro:

```
ln -s ../../../../../../vlsi/macros/sram22_64x32m4w32_macro/sram22_64x32m4w32_macro.v sram22_64x32m4w32_macro.v
```

To integrate this into your Chisel generator, add the following code to the bottom of your `SramBist.scala` file:

```scala
class SramBlackBox(numWords: Int, dataWidth: Int, muxRatio: Int, maskGranularity: Int)
    extends BlackBox
    with HasBlackBoxResource {
  val wmaskWidth = dataWidth / maskGranularity
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val we = Input(Bool())
    val wmask = Input(UInt(wmaskWidth.W))
    val addr = Input(UInt(log2Ceil(numWords).W))
    val din = Input(UInt(dataWidth.W))
    val dout = Output(UInt(dataWidth.W))
  })

  override val desiredName =
    s"sram22_${numWords}x${dataWidth}m${muxRatio}w${maskGranularity}_macro"

  // TODO: Point to the verilog source file that is we just defined at
  // `generators/srambist/src/main/resources/vsrc/sram22_64x32m4w32_macro.v`.
  //
  // Instead of hardcoding the SRAM name, use `desiredName`.
  ???
}
```

Using Chisel's [`BlackBox` documentation](https://www.chisel-lang.org/docs/explanations/blackboxes#blackboxes-with-verilog-in-a-resource-file), fill in the `???` to link the appropriate verilog file.

### MMIO interface

We will now define the MMIO interface. At the bottom of `SramBist.scala`, add the following code:

```scala
// The SRAM BIST doesn't expose any ports outside of its MMIO registers.
trait SramBistTopIO extends Bundle {}

trait SramBistTop extends HasRegMap {
  val io: SramBistTopIO
  val clock: Clock
  val reset: Reset

  val data0 = RegInit(0.U(32.W))
  val data1 = RegInit(0.U(32.W))
  val ex = Wire(new DecoupledIO(Bool()))
  val bistReset = Wire(Bool());

  // Allow restart of BIST at any point.
  ex.ready := true.B
  // Hold reset for at least one full cycle after EX register written.
  val pastValid = RegInit(false.B)
  pastValid := ex.valid
  bistReset := false.B
  when (ex.valid || pastValid) {
    bistReset := true.B
  }

  val bist = withReset(bistReset) {
    Module(new SramBist(64, 32, Seq(Pattern.W0, Pattern.R0, Pattern.W1, Pattern.R1)))
  }
  val sram = Module(new SramBlackBox(64, 32, 4, 32))

  sram.io.clk := clock

  // TODO: Connect up the remainder of `bist` and `sram` ports.
  ???

  regmap(
    0x00 -> Seq(
      RegField.w(32, data0)
    ),
    0x04 -> Seq(
      RegField.w(32, data1)
    ),
    0x08 -> Seq(
      RegField.r(1, bist.io.done)
    ),
    0x0C-> Seq(
      RegField.r(1, bist.io.fail)
    ),
    0x10 -> Seq(
      RegField.w(1, ex)
    ),
  )
}
```

This code declares the MMIO registers, their widths, and their memory offsets. It also instantiates
both the BIST and the SRAM under test. Fill in the `???` to connect up the remainder of the BIST and SRAM
ports. Descriptions of the 5 MMIO registers are included below:

| Register | Description |
| --- | --- |
| DATA0 | The `data0` input to the BIST. |
| DATA1 | The `data1` input to the BIST. |
| DONE | The done bit outputted by the BIST. |
| FAIL | The fail bit outputted by the BIST. |
| EX | The execute register, which resets the BIST when written to. |

> [!TIP]
> For the SRAM write mask, consider that the SRAM word size is 32 bits and that the mask granularity is 32 bits.
> Since mask granularity declares the number of bits that each bit of the mask covers, the write mask
> is 1 bit wide.
>
> You can keep the write mask high at all times since the BIST always writes full words at a time.

### Routing memory requests

We will now need to define the router that will take memory requests from the Rocket core and send them
to our SRAM BIST peripheral when appropriate. Rocket makes its memory requests over
[TileLink](https://bar.eecs.berkeley.edu/projects/tilelink.html), so we will need
to create a `TLRegisterRouter`. Add the following to `SramBist.scala`:

```scala
class SramBistTL(params: SramBistParams, beatBytes: Int)(implicit p: Parameters)
  extends TLRegisterRouter(
    params.address, "srambist", Seq("eecs251b,srambist"),
    beatBytes = beatBytes)(
      new TLRegBundle(params, _) with SramBistTopIO)(
      new TLRegModule(params, _, _) with SramBistTop)
```

### Configuration

We now define a configuration will allow us to optionally instantiate our SRAM BIST peripheral
and connect it to a Rocket core. Add the following to `SramBist.scala`:

```scala
case class SramBistParams(
  address: BigInt = 0x4000,
)

case object SramBistKey extends Field[Option[SramBistParams]](None)

trait CanHavePeripherySramBist { this: BaseSubsystem =>
  private val portName = "srambist"

  // If `SramBistKey` is declared, initialize and connect the BIST peripheral.
  val srambist = p(SramBistKey) match {
    case Some(params) => {
      val srambist = LazyModule(new SramBistTL(params, pbus.beatBytes)(p))
      pbus.coupleTo(portName) { srambist.node := TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _ }
      Some(srambist)
    }
    case None => None
  }
}

trait CanHavePeripherySramBistImp extends LazyModuleImp {
  val outer: CanHavePeripherySramBist
}


class WithSramBist(params: SramBistParams) extends Config((site, here, up) => {
  // Store the parameters in the configuration under `SramBistKey`.
  case SramBistKey => Some(params)
})
```

The traits `CanHavePeripherySramBist` and `CanHavePeripherySramBistImp` tell Chipyard how to instantiate an SRAM
BIST if the configuration `WithSramBist` is provided. If you are interested in how exactly `WithSramBist` works, you can read [this page](https://chipyard.readthedocs.io/en/latest/Advanced-Concepts/CDEs.html) of the Chipyard documentation.

To tell Chipyard about our new traits, we need to add them
to `generators/chipyard/src/main/scala/DigitalTop.scala`. Determine what lines you need to add to this file
and add them.

> [!TIP]
> You will need to add exactly two lines, one in `DigitalTop` and one in `DigitalTopModule`.
> As a hint, the GCD peripheral example is very similar to the peripheral we just created.

Finally, we can create a Rocket configuration that instantiates both a small Rocket core and our SRAM BIST
peripheral.
the number of SRAMs required on the chip. Add
the following to the top of `generators/chipyard/src/main/scala/config/RocketConfigs.scala`:

```scala
class SramBistConfig extends Config(
  ??? ++ // TODO: Instantiate your SRAM BIST.
  new freechips.rocketchip.subsystem.WithNSmallCores(1) ++
  new testchipip.soc.WithNoScratchpads ++ // Remove subsystem scratchpads
  new testchipip.serdes.WithSerialTLMem(size = (1 << 30) * 1L) ++ // Configure the off-chip memory accessible over serial-tl as backing memory
  new freechips.rocketchip.subsystem.WithNoMemPort ++ // Remove off-chip AXI port
  new testchipip.soc.WithOffchipBusClient(freechips.rocketchip.subsystem.MBUS) ++ // off-chip bus connects to MBUS to provide backing memory
  new testchipip.soc.WithOffchipBus ++ // Attach off-chip bus
  new chipyard.config.WithBroadcastManager ++ // Replace L2 with a broadcast hub for coherence
  new chipyard.config.AbstractConfig)
```

Fill out the `???` to instantiate your SRAM BIST with the default parameters (i.e. `address = 0x4000`).
This address defines the base address at which the addresses of MMIO registers for the SRAM BIST begin.
The offsets we defined in the `regmap` earlier are relative to this address
(i.e. the EX register will be at 0x4010).

> [!TIP]
> The `WithSramBist` configuration for instantiating your BIST that you defined earlier
> is in the package `srambist`. To create an instance of default parameters, you can
> write `srambist.SramBistParams()`.

If you are curious, the additional configurations allow us to provide off-chip memory over TileLink, reducing
the number of SRAMS that need to be on-chip.

## Baremetal testing

Now, all we need to do is test that everything works. Conceptually, we should be able to run instructions
on the Rocket core that write to the MMIO registers and run the BIST. To do this, we will write a C program,
compile it to assembly, and run it on the Rocket core.

We have already written a test to exercise your BIST peripheral at `tests/srambist.c`. However, it does not yet
compile since the MMIO register addresses have not yet been defined. Replace the placeholders with the addresses
of each MMIO register.

> [!TIP]
> Look at the default base address in your `SramBistParams` declaration as well as the `regmap` we defined
> earlier. If you determine that the address of the `DATA0` register is `0x1234`, your code should look like this:
>
> ```
> #define SRAM_BIST_DATA0 0x1234
> ```

You are now done writing all the code you need to integrate and test your peripheral! To run everything, run
the following commands from the root directory of the Chipyard repo (this may take a several minutes):

```
cd tests/
make
cd ../sims/vcs
make CONFIG=SramBistConfig BINARY=../../tests/srambist.riscv run-binary
```

> [!WARNING]
> If you make changes to `tests/srambist.c` without running `make` in the `tests/` directory, your changes
> will not be reflected in the binary and you will end up running and old version of your code on the Rocket
> core. Whenever you make changes, remember to run `make` to recompile the binary.

> [!TIP]
> To produce waveforms for debugging purposes, you can run the following instead from the `sims/vcs` directory:
>
> ```
> make USE_VPD=1 CONFIG=SramBistConfig BINARY=../../tests/srambist.riscv run-binary-debug
> ```
> 
> You can then view the waveform by running DVE:
>
> ```
> dve -vpd output/chipyard.harness.TestHarness.SramBistConfig/srambist.vpd
> ```

> [!TIP]
> A common cause for timeout errors is forgetting to check the SRAM data output only a cycle after a read 
> has been initiated. Trying to validate output on the same cycle as the read will cause undefined behavior.

Your UART output (which can be found by looking at standard output or the generated
`sims/vcs/output/chipyard.harness.TestHarness.SramBistConfig/srambist.log`) should look exactly like the following if
your SRAM BIST is working correctly:

```
[UART] UART0 is here (stdin/stdout).
SRAM BIST succeeded!
SRAM BIST failed!
SRAM BIST succeeded!
```

The BIST is expected to catch a fault in the SRAM, causing the second line to print a failure.

## Conclusion

By going through the above flow, you should be able to integrate and test any custom block using Chipyard's
infrastructure. In later labs, you will see how you can use Chipyard's VLSI flow to create a layout for
the SoC you just designed.

## Deliverables

You should only need to edit the following files:
- `build.sbt`
- `generators/chipyard/src/main/scala/DigitalTop.scala`
- `generators/chipyard/src/main/scala/config/RocketConfigs.scala`
- `generators/srambist`
- `tests/srambist.c`

Submit a screenshot of your log that shows the desired UART output to Gradescope. Make sure that your screenshot includes your terminal prompt
such that we can see the repo path and activated conda environment, otherwise we will not be able to give credit.

Additionally, make sure to push your latest code to your repo.
