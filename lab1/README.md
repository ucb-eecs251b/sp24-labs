# Lab 1: Chisel

In this lab, you'll learn how to create and test a practical generator in Chisel.

> [!NOTE]
> It is highly recommended that you go through at least section 2 of the 
> [Chisel bootcamp](https://github.com/freechipsproject/chisel-bootcamp) before going through the lab. 
> You might find it helpful to go through other sections as well 
> if you have more time.

## Setup

You may complete this lab either locally or on the instructional machines. Either way, you should set up your instructional account now since future labs will need to be done on the instructional machines.

To begin, accept the GitHub classroom assignment using the link provided on Ed. This will create a personal
repo for you to push your code.

### Instructional account

Create an instructional account at 
[https://acropolis.cs.berkeley.edu/~account/webacct/](https://acropolis.cs.berkeley.edu/~account/webacct/). 
After logging in via "Login using your Berkeley CalNet ID", you should find that you can create an account for
the eecs251b group.

All of our work will occur on the instructional servers on the `eda-[1-8].eecs.berkeley.edu` 
machines. You may connect to these machine directly via SSH with X11 Forwarding or via [X2Go](https://wiki.x2go.org/doku.php/download:start).
Log in with the account created above.

### GitHub

In this class, we will be using GitHub for coding assignments and collaboration. To use GitHub on the instructional machines, you will need to complete the steps below.

> [!IMPORTANT]
> Make sure that you complete the following steps on an instructional machine.

* [Configure your name in git](https://docs.github.com/en/get-started/getting-started-with-git/setting-your-username-in-git)
* [Configure your email address in git](https://docs.github.com/en/account-and-profile/setting-up-and-managing-your-personal-account-on-github/managing-email-preferences/setting-your-commit-email-address).
  Use your Berkeley email address.
* [Create an SSH key](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/generating-a-new-ssh-key-and-adding-it-to-the-ssh-agent?platform=linux).
  If you create your SSH key with a password, you'll need to type your password every time you push/pull. We recommend leaving the password blank, but it's your choice.
* [Add the SSH key to your GitHub account](https://docs.github.com/en/authentication/connecting-to-github-with-ssh/adding-a-new-ssh-key-to-your-github-account).

Once you are done, be sure that you can authenticate to GitHub via SSH by running:

```bash
ssh -T git@github.com
```

If you have configured things correctly, you should see something like this:
```bash
Hi <YOUR GITHUB USERNAME>! You've successfully authenticated, but GitHub does not provide shell access.
```

### Local setup

> [!NOTE]
> You only need to complete this if you would like to work on the lab locally. If you are planning
> on using the instructional machines, skip to the next section.

> [!WARNING]
> You will need to install several things to get your local setup working, and you may run
> into a few issues while trying to set it up. While the TAs will help you work through any issues,
> it is recommended to use the instructional machines if you are not comfortable installing
> things locally.

Install the following:

- [JDK 8 or newer](https://docs.oracle.com/en/java/javase/18/install/overview-jdk-installation.html).
    You can check your installed version using `java --version`.
- [SBT](https://www.scala-sbt.org/download.html)

If you would like to debug waveforms locally as well, you should install `gtkwave` as outlined in 
the [Debugging](#debugging) section.

### Starter code

> [!IMPORTANT]
>
> If you are using the instructional machines, you must work in the `/scratch` directory on a lab machine 
> of your choice. Since `/scratch` is not automatically backed up, you will need to login to the 
> same instructional server each time. Chipyard will generate too much data to fit in
> your home directory.
> 
> To create your own scratch directory, run the following commands before downloading the starter code.
> 
> ```
> mkdir -m 0700 -p /scratch/$USER
> cd /scratch/$USER
> ```

To download the starter code, run the following commands on the machine you
plan on using:

```
# e.g. git clone git@github.com:ucb-eecs251b/sp24-labs-<username>.git chipyard
git clone <your-lab-repo> chipyard 
cd chipyard
# e.g. git remote add skeleton https://github.com/ucb-eecs251b/sp24-labs.git
git remote add skeleton https://github.com/ucb-eecs251b/<this-repo-name>.git
git pull skeleton main
```

## Background

An [SRAM built-in self-test (BIST)](https://booksite.elsevier.com/9780123705976/errata/13~Chapter%2008%20MBIST.pdf) 
is a circuit that runs a pattern of reads/writes against an SRAM to verify its behavior. 
An SRAM BIST benefits significantly from Chisel's features as it needs to be highly parametrizable to test different operation patterns and SRAM sizes.

> [!TIP]
> A more complex version of this circuit was [taped out](https://github.com/ucb-bar/stac-top) 
> in June 2023 to test SRAM macros generated by [SRAM22](https://github.com/rahulk29/sram22).

## Implementation

First, we will implement the BIST in `srambist/src/main/scala/srambist/SramBist.scala`.

### Generator parameters

Looking at the class definition, we can see that `SramBist` takes in 3 parameters:

| Parameter | Explanation |
| --- | --- |
| `numWords` | Number of words in the SRAM. The number of address bits is given by `log2Ceil(numWords)` (i.e. the SRAM is word addressed). |
| `dataWidth` | The size of each SRAM word. This is also the width of the SRAM data input and output. |
| `patterns` | A sequence of patterns to run against the SRAM. Each pattern is one of the `Pattern` variants found at the top of the file. |

This parametrization allows `SramBist` to be easily used with different SRAM sizes, and 
allows us to modify the testing pattern by simply changing a line of code.

### IO

The IO bundle consists of a couple signals for interfacing with the SRAM under test and a few others for 
controlling the BIST.

| Signal | Explanation |
| --- | --- |
| `we` | Write-enable. |
| `addr` | SRAM address. |
| `din` | Data input to the SRAM. |
| `dout` | Data output from the SRAM. |
| `data0` | The data to write/read for `W0` and `R0` patterns. |
| `data1` | The data to write/read for `W1` and `R1` patterns. |
| `done` | Asserted once the BIST is complete and remains high. |
| `fail` | Asserted if the BIST has failed on the same cycle as done and remains high. |

### Deliverable

Fill out the body of `SramBist` (i.e. replace the `???`) to run the desired patterns against the SRAM under test, then
set the `done` and `fail` signals appropriately. 

On reset, `done` should be set low. You should then run each pattern until a read operation
yields an unexpected result or all patterns have been run, then set `done` to high. On that cycle, 
`fail` should be high only if the BIST completed on a failed read. Both signals should remain stable 
once `done` has been set high. 

To test your code, run the following command from the `srambist/` directry:

```
sbt 'testOnly srambist.SramBistSpec'
```

Once you have finished this part, you should be passing all tests except the unimplemented 
`Stuck-at-0 SRAM should fail BIST` test.

A couple things to help you out:
- The behavioral model of a functional SRAM looks like this:

  ```scala
  class Sram(numWords: Int, dataWidth: Int)
      extends Module {
    val io = IO(new Bundle {
      val we = Input(Bool())
      val addr = Input(UInt(log2Ceil(numWords).W))
      val din = Input(UInt(dataWidth.W))
      val dout = Output(UInt(dataWidth.W))
    })

    
    // This is the equivalent of a 2D register array in Verilog where the 
    // first index is the SRAM address and the second index locates 
    /// a bit within the word at that address.
    val mem = SyncReadMem(
      numWords,
      UInt(dataWidth.W)
    )

    // Unless we are specifically reading (i.e. `io.we` is low),
    // the output is undefined.
    io.dout := DontCare

    // Access the word at the desired address.
    val rdwrPort = mem(io.addr)
    when(io.we) {
      // If write is enabled, write the value of `io.din` to the
      // SRAM on the next rising clock edge.
      rdwrPort := io.din
    }.otherwise {
      // If write is enabled, read the value in the SRAM
      // to `io.dout` on the next rising clock edge.
      io.dout := rdwrPort
    }
  }
  ```
- You will probably need one register to keep track of which pattern you are running,
  another to keep track of the address within the pattern that you are on, and a couple more
  for tracking completion and failure. Keep in mind that SRAMs will only output data
  on the next clock cycle, so you may need registers for output validation.
- `RegInit` creates a register with a specified reset value.
- Assuming you have a register `ctr` with the Chisel type `UInt` that keeps track of the current pattern being run,
  the following code will allow you to define behavior specific to each pattern.

  ```scala
  patterns.zipWithIndex.foreach { // A Scala `foreach` loop to iterate over the Scala sequence `patterns`.
    case (pattern, idx) => {
      when(ctr === idx.U) { // Chisel `when` statement to match against the hardware register `ctr`.
        pattern match { // Scala `match` statement to match against a specific Scala `Pattern` enumeration.
          case Pattern.W0 => {
            // Pattern W0 behavior
          }
          case Pattern.W1 => {
            // Pattern W1 behavior
          }
          case Pattern.R0 => {
            // Pattern R0 behavior
          }
          case Pattern.R1 => {
            // Pattern R1 behavior
          }
        }
      }
    }
  };
  ```
- Don't mix up Scala and Chisel types! Before coding, identify which types are from Scala 
  and which are from Chisel.
  For example, `Int` and `Boolean` are Scala types, while `UInt` and `Bool` are
  Chisel types.

  Whenever you have a Scala type, you can use it in normal Scala code but cannot use
  it with Chisel code. The reverse is true for Chisel types. As an example, you can do
  the following:

  ```scala
  var x: Int = 5
  if (x == 5) {
      x = x + 1
  }

  var y: UInt = 5.U
  when (y === 5.U) {
      y := y + 1.U
  }
  ```

  However, you cannot do this:

  ```scala
  var x: Int = 5
  when (x === 5) { // Cannot use `when` with Scala `Boolean`s or `===` with Scala `Int`s.
      x := x + 1 // Cannot use `:=` with Scala `Int`s.
  }

  var y: UInt = 5.U
  if (y == 5.U) { // Cannot use `if` with Chisel `Bool`s or `==` with Chisel `UInt`s.
      y = y + 1.U // Cannot use `=` with Chisel `UInt`s.
  }
  ```
- In Scala, `var` is used to declare variables that can be changed and `val` is used to declare constants.
  **When writing Chisel, you should essentially always use `val`.**

#### Example execution

Suppose we have the following instantiation of your BIST:

```scala
val bist = Module(new SramBist(64, 32, Seq(Pattern.W0, Pattern.R0, Pattern.W1, Pattern.R1)))
```

Suppose we then pass the following inputs to the BIST interface:

```scala
bist.io.data0 := "hDEADBEEF".U
bist.io.data1 := "hCAFEF00D".U
```

Upon reset, your BIST is expected to output `io.done := false.B`. It should then do the following:
- Since the first pattern is `Pattern.W0`, write `"hDEADBEEF".U` to address 0 by setting 
  `io.we := true.B`, `io.addr := 0.U`, and `io.din := io.data0`.
- Once the first write completes on the next rising clock edge, write "hDEADBEEF".U to address 1
  by updating `io.addr := 1.U`. Repeat up to and including `io.addr := 63.U`.
- The next pattern is `Pattern.R0`, meaning the BIST should now read address 0 by setting 
  `io.we := false.B` and `io.addr := 0.U`. The read will complete on the next rising clock edge, so
  wait until the next cycle and validate that `io.dout === io.data0`.
- On the same cycle that the BIST validates the SRAM output, change the inputs to the SRAM to read address 1
  (i.e. `io.addr := 1.U). On the next cycle, validate that `io.dout === io.data0` and update `io.addr := 2.U`.
  Repeat up to and including `io.addr := 63.U`.
- In the case that `io.dout =/= io.data0` on one of the reads, the BIST should fail by setting `io.done := true.B`
  and `io.fail := true.B`. These signals should not change once you set `io.done := true.B`.
- For the `Pattern.W1` and `Pattern.R1` patterns, repeat the above process except using `io.data1` instead of `io.data0`.
- Once all of the patterns complete, set `io.done := true.B` and `io.false := false.B` on the same cycle. 
  These signals should not change once you set `io.done := true.B`.

> [!CAUTION]
> It is easy to have an off by one error on the final read since a naive implementation that sets `io.done := true.B`
> immediately after the last read is initiated will not be able to validate the last read's output. You will need to
> wait an extra cycle before setting `io.done := true.B` so that the last read's output can be used to determine if
> `io.fail` should be asserted.

### Debugging

#### Waveforms

After running  a simulation, you should find a `*.vcd` file containing the waveforms from your simulation 
in the `test_run_dir` directory. You can use any waveform viewer (e.g. DVE and Simvision)
to navigate the file, but we recommend using [`gtkwave`](https://sourceforge.net/projects/gtkwave/). 

> [!WARNING]
> Downloading `gtkwave` from SourceForge no longer works on Mac since it is no longer officially supported.
> However, you can still [install it from source](https://github.com/gtkwave/gtkwave):
> 
> ```
> brew install meson gtk-mac-integration desktop-file-utils shared-mime-info
> git clone https://github.com/gtkwave/gtkwave.git
> cd gtkwave
> meson setup build
> meson compile -C build
> cd build
> meson install
> ```

To open `gtkwave`, run the following command (if you are on an instructional machine, make sure you are using X2GO):

```
gtkwave test_run_dir/<AutoGeneratedName>/<ModuleName>.vcd &
```

You can add signals to the waveform view by double clicking them in the panel on the left.


#### Print statements

If you want to debug locally and do not want to install a waveform viewer, you can simply use
[Chisel print statements](https://www.chisel-lang.org/docs/explanations/printing) to print out relevant variables.

## Testing

Now, we'll write a basic test to ensure that our BIST catches a common failure mode for SRAMs: 
stuck-at faults. Take a look at `srambist/src/test/scala/srambist/SramBistSpec.scala` to see how the 
existing tests are written. Then, finish the `Stuck-at-0 SRAM should fail BIST` test by replacing 
the `???`s to instantiate a 128x16 SRAM with a failure mode of `FailureMode.StuckAt0`. This failure mode
indicates that bits, once they become 0, will remain 0 while the SRAM is powered.

Come up with a sequence of patterns and the appropriate `data0` and `data1` values that will detect a 
stuck-at-0 in an address of the SRAM, and ensure that your BIST correctly indicates a failure. 
You will probably want to copy the format of another test and modify the necessary parameters.

Once you are done, run the following from the `srambist/` directory to ensure that all of the tests pass:

```
sbt 'testOnly srambist.SramBistSpec'
```

## Conclusion

Building a highly-parametrizable BIST is made possible by Chisel's powerful features for writing
RTL generators. If you're feeling brave, you can try redoing this lab in Verilog.

## Deliverables

You should only need to edit the following files:
- `srambist/src/main/scala/srambist/SramBist.scala`
- `srambist/src/test/scala/srambist/SramBistSpec.scala`

Submit your final code by pushing to your GitHub repo and submitting the repo to the Gradescope
autograder assignment. Your score on the autograder will be your score for the assignment.
It is recommended that you only submit to the autograder once all of the tests are passing locally.

