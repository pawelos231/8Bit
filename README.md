# MOS6502 Emulator and Visualizer Documentation

This project implements a MOS6502 CPU emulator along with a Swing-based visualizer/debugger. The emulator simulates the classic 6502 processor’s registers, memory, instruction execution, addressing modes, and stack operations. The visualizer provides a graphical user interface (GUI) to step through instructions, view registers, flags, memory, and the stack, as well as to control the execution speed.

---

## Table of Contents

- [Overview](#overview)
- [MOS6502 CPU Emulator (core.MOS6502)](#mos6502-cpu-emulator-coremos6502)
  - [Registers and Memory](#registers-and-memory)
  - [Status Flags](#status-flags)
  - [Memory Layout](#memory-layout)
  - [Addressing Modes](#addressing-modes)
  - [Stack Operations](#stack-operations)
  - [Instruction Set](#instruction-set)
  - [Core Methods](#core-methods)
- [Visualizer (vis.Visualizer)](#visualizer-visvisualizer)
  - [GUI Layout](#gui-layout)
  - [Components and Their Functions](#components-and-their-functions)
  - [Event Listeners and UI Updates](#event-listeners-and-ui-updates)
- [Usage](#usage)
- [Notes and Future Improvements](#notes-and-future-improvements)

---

## Overview

This project is divided into two main packages:

- **core**: Contains the MOS6502 emulator core, which implements the processor's registers, memory, instruction decoding/execution, addressing modes, and stack handling.
- **vis**: Contains a Swing-based visualization layer that provides a debugger GUI for stepping through the execution, viewing registers, flags, memory dumps, and the stack.

---

## MOS6502 CPU Emulator (core.MOS6502)

The `MOS6502` class emulates the classic 6502 CPU. It includes:

### Registers and Memory

- **Accumulator (A):** Main arithmetic register (8-bit).
- **X Register (X):** Index register (8-bit), used for addressing and looping.
- **Y Register (Y):** Second index register (8-bit), used for addressing.
- **Stack Pointer (S):** Points to the top of the stack (located in memory from `0x0100` to `0x01FF`).
- **Status Register (P):** Holds the processor flags (8-bit).
- **Program Counter (PC):** Points to the current instruction (16-bit).

Memory is modeled as a 64KB array.

### Status Flags

The following flags are defined in the status register:

- **FLAG_CARRY (0x01):** Set if the last operation produced a carry out of bit 7.
- **FLAG_ZERO (0x02):** Set if the result of an operation is zero.
- **FLAG_INTERRUPT (0x04):** When set, interrupts are disabled.
- **FLAG_DECIMAL (0x08):** Set if the CPU is in Binary Coded Decimal (BCD) mode.
- **FLAG_BREAK (0x10):** Set when a software interrupt (BRK) is executed.
- **FLAG_UNUSED (0x20):** Unused, but always set to 1.
- **FLAG_OVERFLOW (0x40):** Set if an arithmetic operation overflows.
- **FLAG_NEGATIVE (0x80):** Set if the result is negative.

### Memory Layout

- **General Memory:** `0x0000` to `0xFFFF`.
- **Stack:** Fixed between `0x0100` and `0x01FF`.
- **Reset Vector:** The initial value of the PC is loaded from addresses `0xFFFC` (low byte) and `0xFFFD` (high byte).

### Addressing Modes

The emulator supports the following addressing modes:

- **IMMEDIATE:** Operand is the next byte (e.g., `#$nn`).
- **ZERO_PAGE:** Uses a one-byte address (first 256 bytes).
- **ZERO_PAGE_X / ZERO_PAGE_Y:** Zero page addressing with X or Y register offset.
- **ABSOLUTE:** Uses a 16-bit address.
- **ABSOLUTE_X / ABSOLUTE_Y:** Absolute addressing with X or Y offset.
- **ABSOLUTE_INDIRECT:** Indirect addressing with a known 6502 bug (wrap-around).
- **INDIRECT_X / INDIRECT_Y:** Indexed indirect addressing modes.
- **ACCUMULATOR:** Operates directly on the accumulator.
- **RELATIVE:** Used for branch instructions (PC-relative).
- **IMPLIED:** No operand is used.

### Stack Operations

The CPU uses a dedicated stack (in memory from `0x0100` to `0x01FF`) for:
- Storing return addresses during subroutine calls (`JSR`/`RTS`).
- Saving the processor status and PC during interrupts (`BRK`/`RTI`).
- Temporary storage of registers (via `PHA`, `PLA`, `PHP`, `PLP`).

### Instruction Set

Instructions are stored in an array of 256 `Instruction` objects, one per opcode. Each instruction includes:
- **Name (Mnemonic):** e.g., LDA, STA, ADC.
- **Addressing Mode:** Determines how the operand is fetched.
- **Base Cycles:** Clock cycles required for execution.
- **Executor:** A functional interface that implements the instruction’s logic.

Examples include:
- **Load/Store Instructions:** LDA, STA, LDX, LDY, STX, STY.
- **Arithmetic Instructions:** ADC (Add with Carry), SBC (Subtract with Carry).
- **Logical Instructions:** AND, ORA, EOR.
- **Comparison Instructions:** CMP, CPX, CPY, BIT.
- **Branching Instructions:** BCC, BCS, BEQ, BMI, BPL, BVC, BVS, BNE.
- **Subroutine and Stack Operations:** JSR, RTS, PHA, PLA, PHP, PLP, RTI.
- **Shifts and Rotates:** ASL, LSR, ROL, ROR.
- **Miscellaneous:** NOP, JMP, and even an illegal opcode (KIL) which halts the CPU.

### Core Methods

- **reset():** Initializes registers, sets the stack pointer to `0xFD`, loads the PC from the reset vector, and resets the cycle count.
- **step():** Fetches the next opcode, decodes it, executes the corresponding instruction, and updates the PC and cycle counter.
- **run(long stepCount):** Executes a given number of CPU steps.
- **loadProgram(byte[] program, int startAddress):** Loads a program into memory starting at the specified address.
- **readByte(), writeByte(), readWord():** Perform memory operations.
- **pushByte(), popByte(), pushWord(), popWord():** Handle stack operations.
- **Flag Management:** Methods to set, clear, and update flags.
- **Addressing Resolution:** `getAdressByMode(AddressingMode mode)` resolves effective addresses based on the addressing mode.
- **Arithmetic Helpers:** `adc(int value)` and `sbc(int value)` perform arithmetic while updating the flags.

---

## Visualizer (vis.Visualizer)

The `Visualizer` class provides a graphical interface to observe and control the emulation of the MOS6502 CPU using Java Swing.

### GUI Layout

- **Top Panel:** Contains control buttons (Step, Run, Pause, Reset, Load Program) and a speed slider to adjust the execution delay.
- **Left Panel:** Displays registers (A, X, Y, PC) and additional info (Stack Pointer, Cycle Count) in separate panels.
- **Flags Panel:** Displays status flags (Carry, Zero, Interrupt, Decimal, Break, Unused, Overflow, Negative) as read-only checkboxes.
- **Right Panel:** Divided vertically:
  - **Stack Dump (Top 25%):** A scrollable text area showing a hex dump of the stack (addresses `0x0100`–`0x01FF`).
  - **Memory Dump (Bottom 75%):** A scrollable text area showing a formatted hex dump of general memory.
- **Address Viewer and Management Panel:** Located at the bottom of the right panel. It lets you input a hexadecimal address to view a specific memory value and to set the number of bytes to display in the memory dump.

### Components and Their Functions

- **JTextArea (memoryDumpArea, stackDumpArea):** Show the contents of memory and the stack in a formatted hex dump.
- **JTextField (addressField, memoryDumpField):** Allow the user to enter an address and the number of bytes for the memory dump.
- **JSlider (speedSlider):** Controls the delay between CPU steps in run mode.
- **Buttons:**
  - **Step:** Executes a single CPU instruction.
  - **Run:** Continuously executes instructions with the specified delay.
  - **Pause:** Stops continuous execution.
  - **Reset:** Resets the CPU.
  - **Load Program:** (Not yet implemented) Currently displays an "Feature not implemented yet" message.
  - **Read:** Reads the value from memory at the given address and displays it.

### Event Listeners and UI Updates

- **Action Listeners:** Trigger CPU actions (step, run, pause, reset, load program, read address).
- **Document Listeners:** Monitor changes in the memory dump field and update the memory dump display.
- **Component Listeners:** Update the memory and stack dump displays when the text areas are resized.
- **Highlighting:** The current instruction (based on the Program Counter) is highlighted in the memory dump area.

---

## Usage

1. **Initialize the CPU:**  
   Create an instance of `MOS6502`, call `initInstructions()`, set the reset vector, and load a program into memory using `loadProgram()`.
2. **Launch the Visualizer:**  
   Create a `Visualizer` instance with the CPU and call `setVisible(true)` to display the GUI.
3. **Control Execution:**  
   Use the Step, Run, Pause, and Reset buttons to control CPU execution. The memory dump, stack dump, registers, and flags update in real time.
4. **View Memory:**  
   Use the Address Viewer to input a hexadecimal address and click "Read" to see the memory value at that address.

---

## Notes and Future Improvements

- **Load Program Feature:**  
  The Load Program button currently displays an informational message. Future versions may include a file chooser and text editor to load programs.
- **Cycle Counting:**  
  The emulator uses base cycle counts. Additional cycles may be added for page boundary crossings and other effects.
- **Highlighting Improvements:**  
  The current instruction highlighting in the memory dump area may need further refinement when the area is resized.
- **Extended Instruction Set:**  
  Although the official instruction set is implemented, undocumented opcodes could be added if needed.
- **GUI Enhancements:**  
  Future updates might include breakpoints, a log of executed instructions, and memory editing features.

---

Happy Emulating!
