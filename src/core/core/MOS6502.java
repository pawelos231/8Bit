package core;

public class MOS6502 {
    // Registers
    // Typically these are 8-bit. We store them in int with a mask (0xFF) 
    // to prevent overflow beyond 8 bits.
    private int accumulator; // A (Accumulator) main arithmetic register
    private int xRegister; //X (Index Register) used for counters and memory pointers, can be used as iterator in loops
    private int yRegister; //Y (Index Register) used for counters and memory pointers, can be used as iterator in loops
    private int stackPointer; //SP, stack is located at the end of the memory, 0x0100 - 0x01FF it is used to store return addresses and processor state
    private int statusRegister; // Holds the flags
    private int programCounter; // PC, points to the current instruction


    //flags essentialy tells us how an arithmetic or logical operation turned out
    public static final int FLAG_CARRY = 0x01; // set if the last operation caused an overflow from bit 7
    public static final int FLAG_ZERO = 0x02; // set if the result of the operation is zero
    public static final int FLAG_INTERRUPT = 0x04; // disable interrupts 
    public static final int FLAG_DECIMAL = 0x08; // set if the CPU is in BCD mode
    public static final int FLAG_BREAK = 0x10; // set if a software interrupt (BRK instruction) was executed
    public static final int FLAG_UNUSED = 0x20; // unused, always 1
    public static final int FLAG_OVERFLOW = 0x40; // set if the result of the operation is too large a positive number or too small a negative number (excluding the sign bit) to fit in the destination
    public static final int FLAG_NEGATIVE = 0x80; // set if the result of the operation is negative

    private long cycles = 0; //Number of cycles the CPU has executed

    private static final byte[] memory = new byte[64 * 1024]; //64KB of memory

    //addressing mode is a way to tell how and where we fetch the operand for the instruction
    private enum AddressingMode {
        IMMEDIATE, // #$nn,
        ZERO_PAGE, // $nn
        ZERO_PAGE_X, // $nn,X, add the X register to the address and read the value from the zero page
        ZERO_PAGE_Y, // $nn,Y add the Y register to the address and read the value from the zero page
        ABSOLUTE, // $nnnn
        ABSOLUTE_X, // $nnnn,X
        ABSOLUTE_Y, // $nnnn,Y
        ABSOLUTE_INDIRECT, // ($nnnn) for INDIRECT addresing mode there is a specific bug that is called wrap-around bug 
        //where the low byte of the address is fetched from the specified location and the high byte is fetched from the next location example JMP ($12FF) will fetch the low byte from $12FF and the high byte from $1200, this emulator will simulate this bug
        INDIRECT_X, // ($nn,X) we add the X register to the address and then read the address from the zero page
        INDIRECT_Y, // ($nn),Y we read the address from the zero page then add the Y register to the address
        ACCUMULATOR, // used for instructions that operate on the accumulator
        RELATIVE, // relative addressing mode, used for branching instructions, resolves the target address by adding the offset to the program counter
        IMPLIED, // no operand
    }


    public MOS6502() {
        reset();
    }

    //REGISTER GETTERS
    public int getAccumulator() { return accumulator & 0xFF; }      // get the value of the accumulator
    public int getX() { return xRegister & 0xFF; }                  // get the value of the X register
    public int getY() { return yRegister & 0xFF; }                  // get the value of the Y register
    public int getPC() { return programCounter & 0xFFFF; }          // get program counter, 16 bits, whole address
    public int getS()  { return stackPointer & 0xFF; }              // get the value of the stack pointer
    public int getP()  { return statusRegister & 0xFF; }            // get the value of the status register
    public long getCycles() { return cycles; }                      // get the number of cycles

    public void reset() {
        setFlag(FLAG_INTERRUPT, true); // we set the flag to true because we are not in the middle of an interrupt
        setFlag(FLAG_DECIMAL, false); // we are not in BCD mode
        setFlag(FLAG_BREAK, false); // we set the flag to false because we are not in the middle of an interrupt
        setFlag(FLAG_UNUSED, true); // this flag is always set to 1

        // set the stack pointer to the end of the stack
        stackPointer = 0xFD;

        // load programCounter from reset vector
        int low = readByte(0xFFFC);
        int high = readByte(0xFFFD);
        programCounter = (high << 8) | low;

        cycles = 0; // zerujemy licznik cykli
    }

    public void clearMemory() {
        for (int i = 0; i < memory.length; i++) {
            memory[i] = 0;
        }
    }

    //interrupt request - IRQ is a maskable interrupt that can be disabled
    public void irq() {
        if(getFlag(FLAG_INTERRUPT)){
            return;
        }

        pushWord(programCounter);
        pushByte(statusRegister & ~FLAG_BREAK); // B=0 w przypadku IRQ
        setFlag(FLAG_INTERRUPT, true);
        int low = readByte(0xFFFE);
        int high = readByte(0xFFFF);
        programCounter = (high << 8) | low;
        cycles += 7;
    }

    public void nmi() {

        //ignores the interrupt if the interrupt flag is set
        pushWord(programCounter);
        pushByte(statusRegister & ~FLAG_BREAK);
        setFlag(FLAG_INTERRUPT, true);
        int low = readByte(0xFFFA);
        int high = readByte(0xFFFB);
        programCounter = (high << 8) | low;
        cycles += 8;
    }

    private void setFlag(int flag, boolean state) {
        if (state) {
            statusRegister  = statusRegister | flag; // example 1100 | 0010 = 1110 sets the flag to 1
        } else { 
            statusRegister =  statusRegister & ~flag; // example 1110 & ~0010 = 1110 & 1101 = 1100 sets the flag to 0
        }
    }

    private boolean getFlag(int flag) {
        return (statusRegister & flag) > 0; // example 1100 & 0010 = 0010 > 0 returns true
    }

    private void updateZeroFlag(int value) {
        setFlag(FLAG_ZERO, mask(value, 8) == 0); // we mask to 8 bits to prevent overflow
    }

    private void updateNegativeFlag(int value) {
        setFlag(FLAG_NEGATIVE, (value & 0x80) > 0); // 0x80 = 10000000 because we are only interested in the 8th bit, two's complement
    }

    private void updateZeroAndNegativeFlags(int value) {
        updateZeroFlag(value);
        updateNegativeFlag(value);
    }

    //reads the next byte from memory
    private int readByte(int address) {
        address = mask(address, 16); //Mask to 16 bits
        return mask(memory[address], 8); //Mask to 8 bits
    }

    //writes a byte to memory
    private void writeByte(int address, int value){
        address = mask(address, 16); //Mask to 16 bits
        value = mask(value, 8); //Mask to 8 bits
        memory[address] = (byte)value;
    }

    //reads a word (a 16 bit address) from memory
    private int readWord(int addr) {
        int low = readByte(addr);
        int high = readByte(addr + 1);
        return (high << 8) | low;
    }

    //pushes a byte to the stack
    private void pushByte(int value){
        writeByte(0x0100 + mask(stackPointer, 8), value);
        stackPointer = mask(stackPointer - 1, 8);
    }

    //pops a byte from the stack
    private int popByte(){
        stackPointer = mask(stackPointer + 1, 8);
        return readByte(0x0100 + mask(stackPointer, 8));
    }

    //pushes a word to the stack
    private void pushWord(int value){
        //first we push high byte then low byte
        pushByte((value >> 8) & 0xFF);
        pushByte(value & 0xFF);
    }

    private int popWord(){
        int low = popByte();
        int high = popByte();
        return (high << 8) | low;
    }


    public class AdressModeResult {
        public int address;
        public boolean pageBoundaryCrossed;

        AdressModeResult(int address, boolean pageBoundaryCrossed) {
            this.address = address;
            this.pageBoundaryCrossed = pageBoundaryCrossed;
        }
    }


    private AdressModeResult getAdressByMode(AddressingMode mode) {
        switch (mode) {
            case IMMEDIATE -> {
                int addr = programCounter;
                programCounter = mask(programCounter + 1, 16);
                return new AdressModeResult(addr, false);
            }

            case ZERO_PAGE -> {
                int zpAddr = readByte(programCounter);
                programCounter = mask(programCounter + 1, 16);
                return new AdressModeResult(zpAddr, false);
            }

            case ZERO_PAGE_X -> {
                int zpxAddr = readByte(programCounter);
                programCounter = mask(programCounter + 1, 16);
                int finalAddr = mask(zpxAddr + xRegister , 8);
                return new AdressModeResult(finalAddr, false);
            }


            case ZERO_PAGE_Y -> {
                int zpyAddr = readByte(programCounter);
                programCounter = mask(programCounter + 1, 16);
                int finalAddrY = mask(zpyAddr + yRegister , 8);
                return new AdressModeResult(finalAddrY, false);
            }

            case ABSOLUTE -> {
                System.out.printf("PC: %04X\n", programCounter);
                int absAddr = readWord(programCounter);
                programCounter = mask(programCounter + 2, 16);
                return new AdressModeResult(absAddr, false);
            }

            case ABSOLUTE_X -> {
                int absxAddr = readWord(programCounter);
                programCounter = mask(programCounter + 2, 16);
                int finalAddrX = mask(absxAddr + xRegister, 16);
                boolean crossedAbsX = ((absxAddr & 0xFF00) != (absxAddr & 0xFF00));
                return new AdressModeResult(finalAddrX, crossedAbsX);
            }

            case ABSOLUTE_Y -> {
                int absyAddr = readWord(programCounter);
                programCounter = mask(programCounter + 2, 16);
                int finalAddrY = mask(absyAddr + yRegister, 16);
                boolean crossedAbsY = ((absyAddr & 0xFF00) != (absyAddr & 0xFF00));
                return new AdressModeResult(finalAddrY, crossedAbsY);
            }

            case ABSOLUTE_INDIRECT -> {
                int pointerAddr = readWord(programCounter);
                //simulate the bug
                pointerAddr = pointerAddr & 0xFF00 | ((pointerAddr + 1) & 0x00FF);
                programCounter = mask(programCounter + 2, 16);
                int finalAddrIndirect = readWord(pointerAddr);
                return new AdressModeResult(finalAddrIndirect, false);
            }

            case INDIRECT_X -> {
                // ($nn,X)
                int zpIndirectIndexedAddr = readByte(programCounter);
                programCounter = (programCounter + 1) & 0xFFFF;
                int ptr = (zpIndirectIndexedAddr + xRegister) & 0xFF;
                int low = readByte(ptr);
                int high = readByte((ptr + 1) & 0xFF);
                int finalAddr = (high << 8) | low;
                return new AdressModeResult(finalAddr, false);
            }

            case INDIRECT_Y -> {
                // ($nn),Y
                int zpIndexedIndirectAddr = readByte(programCounter);
                programCounter = (programCounter + 1) & 0xFFFF;
                int low = readByte(zpIndexedIndirectAddr);
                int high = readByte((zpIndexedIndirectAddr + 1) & 0xFF);
                int ptr = (high << 8) | low;
                int finalAddr = (ptr + yRegister) & 0xFFFF;
                boolean crossed = (ptr & 0xFF00) != (finalAddr & 0xFF00);
                return new AdressModeResult(finalAddr, crossed);
            }

            case RELATIVE -> {
                int offset = readByte(programCounter);
                programCounter = mask(programCounter + 1, 16);  
                if((offset & 0x80) != 0) {
                    offset = offset | 0xFF00;
                }

                int target = programCounter + offset;
                boolean crossed = (target & 0xFF00) != (programCounter & 0xFF00);
                return new AdressModeResult(target, crossed);
            }

            case IMPLIED, ACCUMULATOR -> {
                return new AdressModeResult(0, false);
            }
        }
        //does not need to use any operands, instruction works immediately
        //zero page addressing mode, only uses the low byte of the address (8 bits), making the instruction faster
        //bug in the 6502
        //if the pointer is on the edge of the page eg #xxFF then high byte reads from $xx00
        //does not have an actual address, operations on registers
        return null;
    }

    //interface for instruction execution that takes an AdressModeResult as a parameter
    @FunctionalInterface
    private interface InstructionExecutor {
        void execute(AdressModeResult amr);
    }

    private static class Instruction {
        String name; //eg LDA                              
        AddressingMode mode; // eg. AddressingMode.IMMEDIATE
        int baseCycles; // base number of cycles
        InstructionExecutor executor; // interface, function that is responsible for handling logic

        public Instruction(String name, AddressingMode mode, int baseCycles, InstructionExecutor executor) {
            this.name = name;
            this.mode = mode;
            this.baseCycles = baseCycles;
            this.executor = executor;
        }
    }

    private final Instruction[] instructions = new Instruction[256];

    public void initInstructions() {

        /*********************************************************************************
        /*                          LOAD AND STORE INSTRUCTIONS 
        /**********************************************************************************
    
        /*LDA instructions */
        // eg LDA #$nn -> opcode 0xA9
        instructions[0xA9] = new Instruction("LDA", AddressingMode.IMMEDIATE, 2, (addrModeRes) -> {
            int value = readByte(addrModeRes.address); //we read the value from the address
            accumulator = mask(value, 8); // we mask to 8 bits to prevent overflow
            updateZeroAndNegativeFlags(accumulator); // we update the zero and negative flags
        });

        // eg LDA $nnnn -> opcode 0xAD
        instructions[0xAD] = new Instruction("LDA", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address); 
            accumulator = mask(value, 8); 
            updateZeroAndNegativeFlags(accumulator); 
        });

        //LDA 0xA5 - zero page addressing mode
        instructions[0xA5] = new Instruction("LDA", AddressingMode.ZERO_PAGE, 3, (addrModeRes) -> {
            int value = readByte(addrModeRes.address); 
            accumulator = mask(value, 8); 
            updateZeroAndNegativeFlags(accumulator); 
        });

        //LDA 0xB5 - zero page addressing mode with X register
        instructions[0xB5] = new Instruction("LDA", AddressingMode.ZERO_PAGE_X, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address); 
            accumulator = mask(value, 8); 
            updateZeroAndNegativeFlags(accumulator); 
        });

        //LDA 0xBD - absolute addressing mode with X register
        instructions[0xBD] = new Instruction("LDA", AddressingMode.ABSOLUTE_X, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address); 
            accumulator = mask(value, 8); 
            updateZeroAndNegativeFlags(accumulator); 
        });

        //LDA 0xB9 - absolute addressing mode with Y register
        instructions[0xB9] = new Instruction("LDA", AddressingMode.ABSOLUTE_Y, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address); 
            accumulator = mask(value, 8); 
            updateZeroAndNegativeFlags(accumulator); 
        });

        //LDA 0xA1 - indirect addressing mode with X register
        instructions[0xA1] = new Instruction("LDA", AddressingMode.INDIRECT_X, 6, (addrModeRes) -> {
            int value = readByte(addrModeRes.address); 
            accumulator = mask(value, 8); 
            updateZeroAndNegativeFlags(accumulator); 
        });

        //LDA 0xB1 - indirect addressing mode with Y register
        instructions[0xB1] = new Instruction("LDA", AddressingMode.INDIRECT_Y, 5, (addrModeRes) -> {
            int value = readByte(addrModeRes.address); 
            accumulator = mask(value, 8); 
            updateZeroAndNegativeFlags(accumulator); 
        });

        //LDA 0xA1 - indirect addressing mode with X register
        instructions[0xA1] = new Instruction("LDA", AddressingMode.INDIRECT_X, 5, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator = mask(value, 8);
            updateZeroAndNegativeFlags(accumulator);
        });

        /* STA instructions */

        //STA - store accumulator in memory $nn -> opcode 0x85
        instructions[0x8D] =  new Instruction("STA", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            writeByte(addrModeRes.address, accumulator);
        });

        //STA - store accumulator in memory 0x95 -> opcode 0x95 
        instructions[0x95] =  new Instruction("STA", AddressingMode.ZERO_PAGE_X, 4, (addrModeRes) -> {
            writeByte(addrModeRes.address, accumulator);
        });

        //STA - store accumulator in memory 0x8D -> opcode 0x8D 
        instructions[0x8D] =  new Instruction("STA", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            writeByte(addrModeRes.address, accumulator);
        });

        //STA - store accumulator in memory 0x9D -> opcode 0x9D
        instructions[0x9D] =  new Instruction("STA", AddressingMode.ABSOLUTE_X, 5, (addrModeRes) -> {
            writeByte(addrModeRes.address, accumulator);
        });

        //STA - store accumulator in memory 0x99 -> opcode 0x99
        instructions[0x99] =  new Instruction("STA", AddressingMode.ABSOLUTE_Y, 5, (addrModeRes) -> {
            writeByte(addrModeRes.address, accumulator);
        });

        //STA - store accumulator in memory 0x81 -> opcode 0x81
        instructions[0x81] =  new Instruction("STA", AddressingMode.INDIRECT_X, 6, (addrModeRes) -> {
            writeByte(addrModeRes.address, accumulator);
        });

        //STA - store accumulator in memory 0x91 -> opcode 0x91
        instructions[0x91] =  new Instruction("STA", AddressingMode.INDIRECT_Y, 6, (addrModeRes) -> {
            writeByte(addrModeRes.address, accumulator);
        });
        
        /* LDX instructions */
        instructions[0xA2] = new Instruction("LDX", AddressingMode.IMMEDIATE, 2, (amr) -> {
            xRegister = readByte(amr.address);
            updateZeroAndNegativeFlags(xRegister);
        });

        instructions[0xA6] = new Instruction("LDX", AddressingMode.ZERO_PAGE, 3, (amr) -> {
            xRegister = readByte(amr.address);
            updateZeroAndNegativeFlags(xRegister);
        });

        instructions[0xB6] = new Instruction("LDX", AddressingMode.ZERO_PAGE_Y, 4, (amr) -> {
            xRegister = readByte(amr.address);
            updateZeroAndNegativeFlags(xRegister);
        });

        instructions[0xAE] = new Instruction("LDX", AddressingMode.ABSOLUTE, 4, (amr) -> {
            xRegister = readByte(amr.address);
            updateZeroAndNegativeFlags(xRegister);
        });

        instructions[0xBE] = new Instruction("LDX", AddressingMode.ABSOLUTE_Y, 4, (amr) -> {
            xRegister = readByte(amr.address);
            updateZeroAndNegativeFlags(xRegister);
        });

        /*LDY instructions */
        instructions[0xA0] = new Instruction("LDY", AddressingMode.IMMEDIATE, 2, (amr) -> {
            yRegister = readByte(amr.address);
            updateZeroAndNegativeFlags(yRegister);
        });

        instructions[0xA4] = new Instruction("LDY", AddressingMode.ZERO_PAGE, 3, (amr) -> {
            yRegister = readByte(amr.address);
            updateZeroAndNegativeFlags(yRegister);
        });
        
        instructions[0xB4] = new Instruction("LDY", AddressingMode.ZERO_PAGE_X, 4, (amr) -> {
            yRegister = readByte(amr.address);
            updateZeroAndNegativeFlags(yRegister);
        });

        instructions[0xAC] = new Instruction("LDY", AddressingMode.ABSOLUTE, 4, (amr) -> {
            yRegister = readByte(amr.address);
            updateZeroAndNegativeFlags(yRegister);
        });

        instructions[0xBC] = new Instruction("LDY", AddressingMode.ABSOLUTE_X, 4, (amr) -> {
            yRegister = readByte(amr.address);
            updateZeroAndNegativeFlags(yRegister);
        });

        // STY
        instructions[0x84] = new Instruction("STY", AddressingMode.ZERO_PAGE, 3, (amr) -> {
            writeByte(amr.address, yRegister);
        });

        instructions[0x94] = new Instruction("STY", AddressingMode.ZERO_PAGE_X, 4, (amr) -> {
            writeByte(amr.address, yRegister);
        });

        instructions[0x8C] = new Instruction("STY", AddressingMode.ABSOLUTE, 4, (amr) -> {
            writeByte(amr.address, yRegister);
        });


        /*********************************************************************************
        /*           Moving data between registers: TXA, TAX, TYA, TAY, TSX, TXS
        /*********************************************************************************

        /* TXA - transfer X to A */
        instructions[0x8A] = new Instruction("TXA", AddressingMode.IMPLIED, 2, (amr) -> {
            accumulator = xRegister & 0xFF;
            updateZeroAndNegativeFlags(accumulator);
        });

        // TAX - transfer A to X
        instructions[0xAA] = new Instruction("TAX", AddressingMode.IMPLIED, 2, (amr) -> {
            xRegister = accumulator & 0xFF;
            updateZeroAndNegativeFlags(xRegister);
        });

        // TYA - transfer Y to A
        instructions[0x98] = new Instruction("TYA", AddressingMode.IMPLIED, 2, (amr) -> {
            accumulator = yRegister & 0xFF;
            updateZeroAndNegativeFlags(accumulator);
        });
        
        // TAY - transfer A to Y
        instructions[0xA8] = new Instruction("TAY", AddressingMode.IMPLIED, 2, (amr) -> {
            yRegister = accumulator & 0xFF;
            updateZeroAndNegativeFlags(yRegister);
        });

        // TSX - transfer stack pointer to X
        instructions[0xBA] = new Instruction("TSX", AddressingMode.IMPLIED, 2, (amr) -> {
            xRegister = stackPointer & 0xFF;
            updateZeroAndNegativeFlags(xRegister);
        });

        // TXS - transfer X to stack pointer
        instructions[0x9A] = new Instruction("TXS", AddressingMode.IMPLIED, 2, (amr) -> {
            stackPointer = xRegister & 0xFF;
        });

        /*********************************************************************************
        /*                     FLAGS: CLI, SEI, CLD, SED, CLV, SEC
        /*********************************************************************************/

        // CLI - Clear Interrupt Disable
        instructions[0x58] = new Instruction("CLI", AddressingMode.IMPLIED, 2, (amr) -> {
            setFlag(FLAG_INTERRUPT, false);
        });

        // SEI - Set Interrupt Disable
        instructions[0x78] = new Instruction("SEI", AddressingMode.IMPLIED, 2, (amr) -> {
            setFlag(FLAG_INTERRUPT, true);
        });

        // CLD - Clear Decimal Mode
        instructions[0xD8] = new Instruction("CLD", AddressingMode.IMPLIED, 2, (amr) -> {
            setFlag(FLAG_DECIMAL, false);
        });

        // SED - Set Decimal Mode
        instructions[0xF8] = new Instruction("SED", AddressingMode.IMPLIED, 2, (amr) -> {
            setFlag(FLAG_DECIMAL, true);
        });

        // CLV - Clear Overflow
        instructions[0xB8] = new Instruction("CLV", AddressingMode.IMPLIED, 2, (amr) -> {
            setFlag(FLAG_OVERFLOW, false);
        });

        // SEC - Set Carry
        instructions[0x38] = new Instruction("SEC", AddressingMode.IMPLIED, 2, (amr) -> {
            setFlag(FLAG_CARRY, true);
        });


        /*********************************************************************************
        /*                        STACK OPERATIONS: PHP, PLP, RTI, RTS
        /*********************************************************************************/

        // PHP - Push Processor Status
        instructions[0x08] = new Instruction("PHP", AddressingMode.IMPLIED, 3, (amr) -> {
            int flags = (statusRegister | FLAG_BREAK | FLAG_UNUSED) & 0xFF;
            pushByte(flags);
        });

        // PLP - Pull Processor Status
        instructions[0x28] = new Instruction("PLP", AddressingMode.IMPLIED, 4, (amr) -> {
            int flags = popByte() & 0xFF;
            flags = (flags & ~FLAG_UNUSED) | FLAG_UNUSED; 
            statusRegister = flags;
        });

        // RTI - Return from Interrupt
        instructions[0x40] = new Instruction("RTI", AddressingMode.IMPLIED, 6, (amr) -> {
            int flags = popByte() & 0xFF;
            flags = (flags & ~FLAG_UNUSED) | FLAG_UNUSED; 
            statusRegister = flags;
            int lo = popByte();
            int hi = popByte();
            programCounter = ((hi << 8) | lo) & 0xFFFF;
        });

        //RTS - Return from Subroutine
        instructions[0x60] = new Instruction("RTS", AddressingMode.IMPLIED, 6, (amr) -> {
            int lo = popByte(); 
            int hi = popByte();
            programCounter = (((hi << 8) | lo) + 1) & 0xFFFF;
        });
        

        /*********************************************************************************
        /*                   PRZESUNIĘCIA / ROTACJE: ASL, LSR, ROL, ROR
        /*********************************************************************************

        /*ASL - Arithmetic Shift Left */
        instructions[0x0A] = new Instruction("ASL", AddressingMode.ACCUMULATOR, 2, (amr) -> {
            int val = mask(accumulator, 8);    //we mask to 8 bits to prevent overflow
            setFlag(FLAG_CARRY, (val & 0x80) != 0); //we set the carry flag to the 7th bit
            val = mask(val << 1, 8);
            accumulator = val;
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x06] = new Instruction("ASL", AddressingMode.ZERO_PAGE, 5, (amr) -> {
            int val = readByte(amr.address);
            setFlag(FLAG_CARRY, (val & 0x80) != 0);
            val = (val << 1) & 0xFF;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x16] = new Instruction("ASL", AddressingMode.ZERO_PAGE_X, 6, (amr) -> {
            int val = readByte(amr.address);
            setFlag(FLAG_CARRY, (val & 0x80) != 0);
            val = (val << 1) & 0xFF;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x0E] = new Instruction("ASL", AddressingMode.ABSOLUTE, 6, (amr) -> {
            int val = readByte(amr.address);
            setFlag(FLAG_CARRY, (val & 0x80) != 0);
            val = (val << 1) & 0xFF;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x1E] = new Instruction("ASL", AddressingMode.ABSOLUTE_X, 7, (amr) -> {
            int val = readByte(amr.address);
            setFlag(FLAG_CARRY, (val & 0x80) != 0);
            val = (val << 1) & 0xFF;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        /* LSR - Logical Shift Right */
        instructions[0x4A] = new Instruction("LSR", AddressingMode.ACCUMULATOR, 2, (amr) -> {
            int val = accumulator & 0xFF;
            setFlag(FLAG_CARRY, (val & 0x01) != 0);
            val = (val >> 1) & 0xFF;
            accumulator = val;
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x46] = new Instruction("LSR", AddressingMode.ZERO_PAGE, 5, (amr) -> {
            int val = readByte(amr.address);
            setFlag(FLAG_CARRY, (val & 0x01) != 0);
            val = (val >> 1) & 0xFF;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });
        instructions[0x56] = new Instruction("LSR", AddressingMode.ZERO_PAGE_X, 6, (amr) -> {
            int val = readByte(amr.address);
            setFlag(FLAG_CARRY, (val & 0x01) != 0);
            val = (val >> 1) & 0xFF;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x4E] = new Instruction("LSR", AddressingMode.ABSOLUTE, 6, (amr) -> {
            int val = readByte(amr.address);
            setFlag(FLAG_CARRY, (val & 0x01) != 0);
            val = (val >> 1) & 0xFF;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x5E] = new Instruction("LSR", AddressingMode.ABSOLUTE_X, 7, (amr) -> {
            int val = readByte(amr.address);
            setFlag(FLAG_CARRY, (val & 0x01) != 0);
            val = (val >> 1) & 0xFF;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        //ROL - Rotate Left, this instruction shifts all bits left by one, the carry flag is shifted into the least significant bit
        instructions[0x2A] = new Instruction("ROL", AddressingMode.ACCUMULATOR, 2, (amr) -> {
            int val = mask(accumulator, 8);
            int oldCarry = getFlag(FLAG_CARRY) ? 1 : 0;
            setFlag(FLAG_CARRY, (val & 0x80) != 0);
            val = ((val << 1) & 0xFF) | oldCarry;
            accumulator = val & 0xFF;
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x26] = new Instruction("ROL", AddressingMode.ZERO_PAGE, 5, (amr) -> {
            int val = readByte(amr.address);
            int oldCarry = getFlag(FLAG_CARRY) ? 1 : 0;
            setFlag(FLAG_CARRY, (val & 0x80) != 0);
            val = ((val << 1) & 0xFF) | oldCarry;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x36] = new Instruction("ROL", AddressingMode.ZERO_PAGE_X, 6, (amr) -> {
            int val = readByte(amr.address);
            int oldCarry = getFlag(FLAG_CARRY) ? 1 : 0;
            setFlag(FLAG_CARRY, (val & 0x80) != 0);
            val = ((val << 1) & 0xFF) | oldCarry;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x2E] = new Instruction("ROL", AddressingMode.ABSOLUTE, 6, (amr) -> {
            int val = readByte(amr.address);
            int oldCarry = getFlag(FLAG_CARRY) ? 1 : 0;
            setFlag(FLAG_CARRY, (val & 0x80) != 0);
            val = ((val << 1) & 0xFF) | oldCarry;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x3E] = new Instruction("ROL", AddressingMode.ABSOLUTE_X, 7, (amr) -> {
            int val = readByte(amr.address);
            int oldCarry = getFlag(FLAG_CARRY) ? 1 : 0;
            setFlag(FLAG_CARRY, (val & 0x80) != 0);
            val = ((val << 1) & 0xFF) | oldCarry;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        //ROR - Rotate Right, this instruction shifts all bits right by one, the carry flag is shifted into the most significant bit
        instructions[0x6A] = new Instruction("ROR", AddressingMode.ACCUMULATOR, 2, (amr) -> {
            int val = accumulator & 0xFF;
            int oldCarry = getFlag(FLAG_CARRY) ? 0x80 : 0;
            setFlag(FLAG_CARRY, (val & 0x01) != 0);
            val = ((val >> 1) & 0xFF) | oldCarry;
            accumulator = val & 0xFF;
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x66] = new Instruction("ROR", AddressingMode.ZERO_PAGE, 5, (amr) -> {
            int val = readByte(amr.address);
            int oldCarry = getFlag(FLAG_CARRY) ? 0x80 : 0;
            setFlag(FLAG_CARRY, (val & 0x01) != 0);
            val = ((val >> 1) & 0xFF) | oldCarry;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x76] = new Instruction("ROR", AddressingMode.ZERO_PAGE_X, 6, (amr) -> {
            int val = readByte(amr.address);
            int oldCarry = getFlag(FLAG_CARRY) ? 0x80 : 0;
            setFlag(FLAG_CARRY, (val & 0x01) != 0);
            val = ((val >> 1) & 0xFF) | oldCarry;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x6E] = new Instruction("ROR", AddressingMode.ABSOLUTE, 6, (amr) -> {
            int val = readByte(amr.address);
            int oldCarry = getFlag(FLAG_CARRY) ? 0x80 : 0;
            setFlag(FLAG_CARRY, (val & 0x01) != 0);
            val = ((val >> 1) & 0xFF) | oldCarry;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        instructions[0x7E] = new Instruction("ROR", AddressingMode.ABSOLUTE_X, 7, (amr) -> {
            int val = readByte(amr.address);
            int oldCarry = getFlag(FLAG_CARRY) ? 0x80 : 0;
            setFlag(FLAG_CARRY, (val & 0x01) != 0);
            val = ((val >> 1) & 0xFF) | oldCarry;
            writeByte(amr.address, val);
            updateZeroAndNegativeFlags(val);
        });

        /*********************************************************************************
        /*                     ARITHEMITC AND LOGICAL INSTRUCTIONS 
        /**********************************************************************************

        /*ADC instructions */

        //ADC - add with carry -> opcode 0x69
        instructions[0x69] = new Instruction("ADC", AddressingMode.IMMEDIATE , 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            adc(value);
        });

        //ADC - add with carry -> opcode 0x65
        instructions[0x65] = new Instruction("ADC", AddressingMode.ZERO_PAGE , 3, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            adc(value);
        });

        //ADC - add with carry -> opcode 0x75
        instructions[0x75] = new Instruction("ADC", AddressingMode.ZERO_PAGE_X , 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            adc(value);
        });

        //ADC - add with carry -> opcode 0x6D
        instructions[0x6D] = new Instruction("ADC", AddressingMode.ABSOLUTE , 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            adc(value);
        });

        //ADC - add with carry -> opcode 0x7D
        instructions[0x7D] = new Instruction("ADC", AddressingMode.ABSOLUTE_X , 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            adc(value);
        });

        //ADC - add with carry -> opcode 0x79
        instructions[0x79] = new Instruction("ADC", AddressingMode.ABSOLUTE_Y , 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            adc(value);
        });

        //ADC - add with carry -> opcode 0x61
        instructions[0x61] = new Instruction("ADC", AddressingMode.INDIRECT_X , 6, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            adc(value);
        });

        //ADC - add with carry -> opcode 0x71
        instructions[0x71] = new Instruction("ADC", AddressingMode.INDIRECT_Y , 5, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            adc(value);
        });

        /* SBC instructions */

        //SBC - subtract with carry -> opcode 0xE9
        instructions[0xE9] = new Instruction("SBC", AddressingMode.IMMEDIATE, 2, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            sbc(value);
        });

        //SBC - subtract with carry -> opcode 0xE5
        instructions[0xE5] = new Instruction("SBC", AddressingMode.ZERO_PAGE, 3, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            sbc(value);
        });

        //SBC - subtract with carry -> opcode 0xF5
        instructions[0xF5] = new Instruction("SBC", AddressingMode.ZERO_PAGE_X, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            sbc(value);
        });

        //SBC - subtract with carry -> opcode 0xED
        instructions[0xED] = new Instruction("SBC", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            sbc(value);
        });

        //SBC - subtract with carry -> opcode 0xFD
        instructions[0xFD] = new Instruction("SBC", AddressingMode.ABSOLUTE_X, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            sbc(value);
        });

        //SBC - subtract with carry -> opcode 0xF9
        instructions[0xF9] = new Instruction("SBC", AddressingMode.ABSOLUTE_Y, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            sbc(value);
        });

        //SBC - subtract with carry -> opcode 0xE1
        instructions[0xE1] = new Instruction("SBC", AddressingMode.INDIRECT_X, 6, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            sbc(value);
        });

        //SBC - subtract with carry -> opcode 0xF1
        instructions[0xF1] = new Instruction("SBC", AddressingMode.INDIRECT_Y, 5, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            sbc(value);
        });

        /* AND Instructions */
        //performs a bitwise AND on the accumulator and the operand and the value from memory or immediate operand

        //AND - logical AND -> opcode 0x29
        instructions[0x29] = new Instruction("AND", AddressingMode.IMMEDIATE, 2, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator = accumulator & value;
            updateZeroAndNegativeFlags(accumulator);
        });

        //AND - logical AND -> opcode 0x25
        instructions[0x25] = new Instruction("AND", AddressingMode.ZERO_PAGE, 3, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator = accumulator & value;
            updateZeroAndNegativeFlags(accumulator);
        });

        //AND - logical AND -> opcode 0x35
        instructions[0x35] = new Instruction("AND", AddressingMode.ZERO_PAGE_X, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator = accumulator & value;
            updateZeroAndNegativeFlags(accumulator);
        });

        //AND - logical AND -> opcode 0x2D
        instructions[0x2D] = new Instruction("AND", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator = accumulator & value;
            updateZeroAndNegativeFlags(accumulator);
        });

        //AND - logical AND -> opcode 0x3D
        instructions[0x3D] = new Instruction("AND", AddressingMode.ABSOLUTE_X, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator = accumulator & value;
            updateZeroAndNegativeFlags(accumulator);
        });

        //AND - logical AND -> opcode 0x39
        instructions[0x39] = new Instruction("AND", AddressingMode.ABSOLUTE_Y, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator = accumulator & value;
            updateZeroAndNegativeFlags(accumulator);
        });

        //AND - logical AND -> opcode 0x21
        instructions[0x21] = new Instruction("AND", AddressingMode.INDIRECT_X, 6, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator = accumulator & value;
            updateZeroAndNegativeFlags(accumulator);
        });

        //AND - logical AND -> opcode 0x31
        instructions[0x31] = new Instruction("AND", AddressingMode.INDIRECT_Y, 5, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator = accumulator & value;
            updateZeroAndNegativeFlags(accumulator);
        });

        /*********************************************************************************
        /*                     BRANCHING INSTRUCTIONS 
        /*********************************************************************************

        /* BCC - branch if carry clear -> opcode 0x90 */
        instructions[0x90] = new Instruction("BCC", AddressingMode.RELATIVE, 2, (addrModeRes) -> {
            if (!getFlag(FLAG_CARRY)) {
                programCounter = addrModeRes.address;
            }
        });

        /* BCS - branch if carry set -> opcode 0xB0 */
        instructions[0xB0] = new Instruction("BCS", AddressingMode.RELATIVE, 2, (addrModeRes) -> {
            if (getFlag(FLAG_CARRY)) {
                programCounter = mask(addrModeRes.address, 16);
            }
        });

        /* BEQ - branch if equal -> opcode 0xF0 */
        instructions[0xF0] = new Instruction("BEQ", AddressingMode.RELATIVE, 2, (addrModeRes) -> {
            if (getFlag(FLAG_ZERO)) {
                programCounter = mask(addrModeRes.address, 16);
            }
        });

        /* BMI - branch if negative -> opcode 0x30 */
        instructions[0x30] = new Instruction("BMI", AddressingMode.RELATIVE, 2, (addrModeRes) -> {
            if (getFlag(FLAG_NEGATIVE)) {
                programCounter = mask(addrModeRes.address, 16);
            }
        });

        /* BPL - branch if positive -> opcode 0x10 */
        instructions[0x10] = new Instruction("BPL", AddressingMode.RELATIVE, 2, (addrModeRes) -> {
            if (!getFlag(FLAG_NEGATIVE)) {
                programCounter = mask(addrModeRes.address, 16);
            }
        });

        /* BVC - branch if overflow clear -> opcode 0x50 */
        instructions[0x50] = new Instruction("BVC", AddressingMode.RELATIVE, 2, (addrModeRes) -> {
            if (!getFlag(FLAG_OVERFLOW)) {
                programCounter = mask(addrModeRes.address, 16);
            }
        });

        /* BVS - branch if overflow set -> opcode 0x70 */
        instructions[0x70] = new Instruction("BVS", AddressingMode.RELATIVE, 2, (addrModeRes) -> {
            if (getFlag(FLAG_OVERFLOW)) {
                programCounter = mask(addrModeRes.address, 16);
            }
        });

        /*BNE - branch if not equal -> opcode 0xD0 */
        instructions[0xD0] = new Instruction("BNE", AddressingMode.RELATIVE, 2, (addrModeRes) -> {
            if(getFlag(FLAG_ZERO)) {
                return;
            }
    
            programCounter = addrModeRes.address & 0xFFFF;
            cycles += 1; 
            if (addrModeRes.pageBoundaryCrossed) {
                cycles += 1; 
            }
        });

        instructions[0x00] = new Instruction("BRK", AddressingMode.IMPLIED, 7, (addrModeRes) -> {
            programCounter = (programCounter) & 0xFFFF; 
            programCounter++; 
            pushWord(programCounter);
            setFlag(FLAG_BREAK, true);
            pushByte(programCounter | FLAG_BREAK);
            setFlag(FLAG_INTERRUPT, true); 
      
            programCounter = readWord(0xFFFE);
        });


        /*********************************************************************************
        /*                     REGISTER AND STACK INSTRUCTIONS 
        /**********************************************************************************


        /* INY - increment Y register by one */
        instructions[0xC8] = new Instruction("INY", AddressingMode.IMPLIED, 2, (addrModeRes) -> {
            yRegister = mask(yRegister + 1, 8);
            updateZeroAndNegativeFlags(yRegister);
        });

        /* DEX  - decrement X register by one */
        instructions[0xCA] = new Instruction("DEX", AddressingMode.IMPLIED, 2, (addrModeRes) -> {
            xRegister = mask(xRegister - 1, 8);
            updateZeroAndNegativeFlags(xRegister);
        });

        /* DEY - decrement Y register by one */
        instructions[0x88] = new Instruction("DEY", AddressingMode.IMPLIED, 2, (addrModeRes) -> {
            yRegister = mask(yRegister - 1, 8);
            updateZeroAndNegativeFlags(yRegister);
        });

        /* INX - increment X register by one */
        instructions[0xE8] = new Instruction("INX", AddressingMode.IMPLIED, 2, (addrModeRes) -> {
            xRegister = mask(xRegister + 1, 8);
            updateZeroAndNegativeFlags(xRegister);
        });

        /* PHA - push accumulator on stack */
        instructions[0x48] = new Instruction("PHA", AddressingMode.IMPLIED, 3, (addrModeRes) -> {
            pushByte(accumulator);
        });

        /* PLA - pull accumulator from stack */
        instructions[0x68] = new Instruction("PLA", AddressingMode.IMPLIED, 4, (addrModeRes) -> {
            accumulator = popByte();
            updateZeroAndNegativeFlags(accumulator);
        });


        /*********************************************************************************
        /*                          OTHER INSTRUCTIONS
        /**********************************************************************************

        
        /* CLC - clear carry flag */
        instructions[0x18] = new Instruction("CLC", AddressingMode.IMPLIED, 2, (addrModeRes) -> {
            setFlag(FLAG_CARRY, false);
        });

        /* NOP - no operation */
        instructions[0xEA] = new Instruction("NOP", AddressingMode.IMPLIED, 2, (addrModeRes) -> {
            //do nothing
        });

        /*JMP - jump to a new location */
        instructions[0x4C] = new Instruction("JMP", AddressingMode.ABSOLUTE, 3, (addrModeRes) -> {
            programCounter = addrModeRes.address;
        });


        /* ORA instructions */

        // ORA - Logical OR -> opcode 0x09
        instructions[0x09] = new Instruction("ORA", AddressingMode.IMMEDIATE, 2, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator |= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // ORA - Logical OR -> opcode 0x05
        instructions[0x05] = new Instruction("ORA", AddressingMode.ZERO_PAGE, 3, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator |= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // ORA - Logical OR -> opcode 0x15
        instructions[0x15] = new Instruction("ORA", AddressingMode.ZERO_PAGE_X, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator |= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // ORA - Logical OR -> opcode 0x0D
        instructions[0x0D] = new Instruction("ORA", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator |= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // ORA - Logical OR -> opcode 0x1D
        instructions[0x1D] = new Instruction("ORA", AddressingMode.ABSOLUTE_X, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator |= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // ORA - Logical OR -> opcode 0x19
        instructions[0x19] = new Instruction("ORA", AddressingMode.ABSOLUTE_Y, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator |= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // ORA - Logical OR -> opcode 0x01
        instructions[0x01] = new Instruction("ORA", AddressingMode.INDIRECT_X, 6, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator |= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // ORA - Logical OR -> opcode 0x11
        instructions[0x11] = new Instruction("ORA", AddressingMode.INDIRECT_Y, 5, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator |= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        /* EOR instructions */

        // EOR - Exclusive OR -> opcode 0x49
        instructions[0x49] = new Instruction("EOR", AddressingMode.IMMEDIATE, 2, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator ^= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // EOR - Exclusive OR -> opcode 0x45
        instructions[0x45] = new Instruction("EOR", AddressingMode.ZERO_PAGE, 3, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator ^= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // EOR - Exclusive OR -> opcode 0x55
        instructions[0x55] = new Instruction("EOR", AddressingMode.ZERO_PAGE_X, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator ^= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // EOR - Exclusive OR -> opcode 0x4D
        instructions[0x4D] = new Instruction("EOR", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator ^= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // EOR - Exclusive OR -> opcode 0x5D
        instructions[0x5D] = new Instruction("EOR", AddressingMode.ABSOLUTE_X, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator ^= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // EOR - Exclusive OR -> opcode 0x59
        instructions[0x59] = new Instruction("EOR", AddressingMode.ABSOLUTE_Y, 4, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator ^= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // EOR - Exclusive OR -> opcode 0x41
        instructions[0x41] = new Instruction("EOR", AddressingMode.INDIRECT_X, 6, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator ^= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        // EOR - Exclusive OR -> opcode 0x51
        instructions[0x51] = new Instruction("EOR", AddressingMode.INDIRECT_Y, 5, (addrModeRes) -> {
            int value = readByte(addrModeRes.address);
            accumulator ^= value;
            updateZeroAndNegativeFlags(accumulator);
        });

        /* CMP instructions */

        // CMP - Compare Accumulator -> opcode 0xC9
        instructions[0xC9] = new Instruction("CMP", AddressingMode.IMMEDIATE, 2, (addrModeRes) -> {
            compare(accumulator, readByte(addrModeRes.address));
        });

        // CMP - Compare Accumulator -> opcode 0xC5
        instructions[0xC5] = new Instruction("CMP", AddressingMode.ZERO_PAGE, 3, (addrModeRes) -> {
            compare(accumulator, readByte(addrModeRes.address));
        });

        // CMP - Compare Accumulator -> opcode 0xD5
        instructions[0xD5] = new Instruction("CMP", AddressingMode.ZERO_PAGE_X, 4, (addrModeRes) -> {
            compare(accumulator, readByte(addrModeRes.address));
        });

        // CMP - Compare Accumulator -> opcode 0xCD
        instructions[0xCD] = new Instruction("CMP", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            compare(accumulator, readByte(addrModeRes.address));
        });

        // CMP - Compare Accumulator -> opcode 0xDD
        instructions[0xDD] = new Instruction("CMP", AddressingMode.ABSOLUTE_X, 4, (addrModeRes) -> {
            compare(accumulator, readByte(addrModeRes.address));
        });

        // CMP - Compare Accumulator -> opcode 0xD9
        instructions[0xD9] = new Instruction("CMP", AddressingMode.ABSOLUTE_Y, 4, (addrModeRes) -> {
            compare(accumulator, readByte(addrModeRes.address));
        });

        // CMP - Compare Accumulator -> opcode 0xC1
        instructions[0xC1] = new Instruction("CMP", AddressingMode.INDIRECT_X, 6, (addrModeRes) -> {
            compare(accumulator, readByte(addrModeRes.address));
        });

        // CMP - Compare Accumulator -> opcode 0xD1
        instructions[0xD1] = new Instruction("CMP", AddressingMode.INDIRECT_Y, 5, (addrModeRes) -> {
            compare(accumulator, readByte(addrModeRes.address));
        });

        /* CPX instructions */

        // CPX - Compare X Register -> opcode 0xE0
        instructions[0xE0] = new Instruction("CPX", AddressingMode.IMMEDIATE, 2, (addrModeRes) -> {
            compare(xRegister, readByte(addrModeRes.address));
        });

        // CPX - Compare X Register -> opcode 0xE4
        instructions[0xE4] = new Instruction("CPX", AddressingMode.ZERO_PAGE, 3, (addrModeRes) -> {
            compare(xRegister, readByte(addrModeRes.address));
        });

        // CPX - Compare X Register -> opcode 0xEC
        instructions[0xEC] = new Instruction("CPX", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            compare(xRegister, readByte(addrModeRes.address));
        });

        /* CPY instructions */

        // CPY - Compare Y Register -> opcode 0xC0
        instructions[0xC0] = new Instruction("CPY", AddressingMode.IMMEDIATE, 2, (addrModeRes) -> {
            compare(yRegister, readByte(addrModeRes.address));
        });

        // CPY - Compare Y Register -> opcode 0xC4
        instructions[0xC4] = new Instruction("CPY", AddressingMode.ZERO_PAGE, 3, (addrModeRes) -> {
            compare(yRegister, readByte(addrModeRes.address));
        });

        // CPY - Compare Y Register -> opcode 0xCC
        instructions[0xCC] = new Instruction("CPY", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            compare(yRegister, readByte(addrModeRes.address));
        });

        /* BIT instructions */

        // BIT - Test Bits -> opcode 0x24
        instructions[0x24] = new Instruction("BIT", AddressingMode.ZERO_PAGE, 3, (addrModeRes) -> {
            doBIT(readByte(addrModeRes.address));
        });

        // BIT - Test Bits -> opcode 0x2C
        instructions[0x2C] = new Instruction("BIT", AddressingMode.ABSOLUTE, 4, (addrModeRes) -> {
            doBIT(readByte(addrModeRes.address));
        });

        /* JSR - Jump to Subroutine -> opcode 0x20 */
        instructions[0x20] = new Instruction("JSR", AddressingMode.ABSOLUTE, 6, (addrModeRes) -> {
            // Push (PC - 1) to stack
            pushWord(programCounter - 1);
            // Jump to subroutine address
            programCounter = addrModeRes.address;
        });


        //ILLEGAL OPCODES - this are the opcodes that are not supported by the 6502 documentation

        /* KIL - Kill the CPU */
        instructions[0x02] = new Instruction("KIL", AddressingMode.IMPLIED, 1, (addrModeRes) -> {
            //do nothing
        });

    }

    /**
     * Porównuje registerValue z value, ustawia Carry jeśli registerValue >= value,
     * ustawia Zero i Negative na podstawie (registerValue - value).
     */
    private void compare(int register, int value) {
        int result = register - value;
        setFlag(FLAG_CARRY, register >= value);
        updateZeroAndNegativeFlags(result);
    }

    /**
     * W BIT testujemy bity w operandzie (wartość z pamięci) wobec akumulatora:
     *  - Z flag -> (A & M) == 0 ?
     *  - N flag -> bit7 of M
     *  - V flag -> bit6 of M
     */
    private void doBIT(int operand) {
        int temp = (accumulator & operand) & 0xFF;
        setFlag(FLAG_ZERO, (temp == 0));
        setFlag(FLAG_NEGATIVE, (operand & 0x80) != 0);
        setFlag(FLAG_OVERFLOW, (operand & 0x40) != 0);
    }

    private void adc(int value) {
        int carry = getFlag(FLAG_CARRY) ? 1 : 0;
        int sum = mask(accumulator + value + carry, 8);
        setFlag(FLAG_CARRY, sum > 0xFF); // if the sum is greater than 255 we set the carry flag

        //overflow occurs when the sign of the result is different from the sign of the two operands
        /*
        * This section was a bit challenging to understand, so I'll explain it step by step.
        *
        * 1. Let's start with the expression `accumulator ^ sum`.
        *    - This checks if the sign of the accumulator and the sum are different.
        *    - For example:
        *      accumulator = 70 (binary: 01000110)
        *      sum = -106 (binary: 10010110)
        *      accumulator ^ sum = 11010000
        *    - The `& 0x80` isolates the most significant bit (sign bit). 
        *      If it is set, the signs of the two operands are different. This part is straightforward.
        *
        * 2. The more complex part is `~(accumulator ^ value)`.
        *    - This checks if the sign of the accumulator and the operand (value) are the same.
        *    - For example:
        *      accumulator = 70 (binary: 01000110)
        *      value = 70 (binary: 01000110)
        *      accumulator ^ value = 00000000
        *      ~(accumulator ^ value) = 11111111
        *    - Since all bits are ones, the next part of the expression remains valid: if the sign of the accumulator and the sum are different, there is a potential overflow.
        *
        * 3. Counter-example: when the accumulator and value have different signs.
        *    - For example:
        *      accumulator = 70 (binary: 01000110)
        *      value = -70 (binary: 10111010)
        *      accumulator ^ value = 11111100
        *      ~(accumulator ^ value) = 00000011
        *    - Here, the signs of the accumulator and the value are different, so no overflow is possible, regardless of the sign difference between the accumulator and the sum.
        *
        * Summary:
        * - Overflow occurs when the accumulator and the operand (value) have the same sign, 
        *   but the sign of the result (sum) differs from both. This logic captures that condition.
        */
        boolean overflow = (~(accumulator ^ value) & (accumulator ^ sum) & 0x80) > 0;
        setFlag(FLAG_OVERFLOW, overflow);
        accumulator = sum & 0xFF;

        updateZeroAndNegativeFlags(accumulator);
    }

    private void sbc(int value) {
        int carry = getFlag(FLAG_CARRY) ? 0 : 1;
        
        // Two's complement, invert the bits and add 1
        int complement = (~value) & 0xFF;
        int sum = mask(accumulator + complement + carry, 8);
        setFlag(FLAG_CARRY, sum > 0xFF);

        boolean overflow = ((accumulator ^ sum) & (accumulator ^ value) & 0x80) > 0;
        setFlag(FLAG_OVERFLOW, overflow);
        accumulator = sum & 0xFF;
        updateZeroAndNegativeFlags(accumulator);
    }


    private int mask(int value, int bits) {
        return value & ((1 << bits) - 1); 
    }

    public void run(long stepCount) {
        for (long i = 0; i < stepCount; i++) {
            step();
        }
    }

    public void setMemory(int address, byte value) {
        writeByte(address, value);
    }

    public int getMemory(int address) {
        return readByte(address);
    }

    public void step() {
        int opcode = readByte(programCounter);
        programCounter = mask(programCounter + 1, 16);
        Instruction instruction = instructions[opcode];

        if (instruction == null) {
             System.err.printf("Niezaimplementowany opcode: 0x%02X, PC=0x%04X\n", opcode, programCounter-1);
             cycles += 2;
             return;
        }

        if ((statusRegister & FLAG_BREAK) != 0) {
            System.out.println("Break flag is set");
            return;
        }

        AdressModeResult amr = getAdressByMode(instruction.mode);
        // System.out.printf("Executing %s at 0x%04X and program counter: 0x%04X \n", instruction.name, amr.address, programCounter);


        //execute the instruction
        instruction.executor.execute(amr);

        cycles += instruction.baseCycles;
        System.out.printf("Cycles: %d, Instruction name: %s\n", cycles, instruction.name);
    }   

    public void loadProgram(byte[] program, int startAddress) {
       for (int i = 0; i < program.length; i++) {
            memory[(startAddress + i) & 0xFFFF] = program[i];
        }
    }

    // public static void main(String[] args) {
    //     MOS6502 cpu = new MOS6502();
    //     cpu.initInstructions();

    //     //reset vector
    //     cpu.setMemory(0xFFFC, (byte)0x00);
    //     cpu.setMemory(0xFFFD, (byte)0x80);


    //     // byte[] demoProgram = {
    //     //     (byte)0xA9, (byte)0x10,   // LDA #$10, this loads the accumulator with the value 0x10 = 16
    //     //     (byte)0x69, (byte)0x05,   // ADC #$05, so add 5 to the accumulator
    //     //     (byte)0xE8,          // INX - increment X register by one
    //     //     (byte)0x00           // BRK - break
    //     // };

    //     byte[] demoProgram2 = {
    //         (byte)0xA9, 0x0A,       // LDA #$0A   ; Załaduj do akumulatora wartość 0x0A (10 dziesiętnie)
    //         (byte)0x18,             // CLC        ; Wyczyść flagę przeniesienia (na wszelki wypadek)
    //         (byte)0x69, 0x05,       // ADC #$05   ; Dodaj 0x05 (5 dziesiętnie) do akumulatora (uwzględniając ewentualne przeniesienie)
    //         (byte)0x8D, 0x00, 0x02, // STA $0200  ; Zapisz wartość z akumulatora do pamięci pod adresem 0x0200
    //         (byte)0x00              // BRK        ; Zatrzymaj (Break)
    //     };


    //     cpu.loadProgram(demoProgram2, 0x8000);

    //     // reset the CPU
    //     cpu.reset();

    //     // run the program
    //     cpu.run(10);
  
    //     // print the state of the CPU
    //     System.out.println("A = " + String.format("0x%02X", cpu.getAccumulator()));
    //     System.out.println("X = " + String.format("0x%02X", cpu.getX()));
    //     System.out.println("PC = " + String.format("0x%04X", cpu.getPC()));
    //     System.out.println("Cycles = " + cpu.getCycles());
    // }
}   


/*
 * Notatki dla siebie:
 * MOS 6502 ma rozne warunki które wpływają na liczbe cykli,
 * 1. Page boundary crossed - gdy operacja przechodzi przez granice strony
 * 2. Branch taken - gdy skok jest wykonywany
 * 3. Specjalne warunki dla niektórych instrukcji: Takie jak błąd przekręcenia strony w JMP (Indirect).
 * 
 * Upewnić się że wszystkie inkrementacje i dekrementacje są poprawne
 * 
 * W intstrukcji RTI Przed przejściem do rutyny obsługi przerwania, 
 * procesor automatycznie zapisuje aktualny programCounter oraz rejestr statusu na stosie. Dzięki temu wie, gdzie wrócić po zakończeniu obsługi przerwania.
 */