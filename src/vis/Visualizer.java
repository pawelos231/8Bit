package vis;
import core.MOS6502;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;

public class Visualizer extends JFrame {
    private core.MOS6502 cpu;
    // Registers
    private JLabel aLabel;
    private JLabel xLabel;
    private JLabel yLabel;
    private JLabel pcLabel;
    private JLabel spLabel;
    private JLabel cyclesLabel;
    
    // Flagi
    private JCheckBox carryCheck;
    private JCheckBox zeroCheck;
    private JCheckBox interruptCheck;
    private JCheckBox decimalCheck;
    private JCheckBox breakCheck;
    private JCheckBox unusedCheck;
    private JCheckBox overflowCheck;
    private JCheckBox negativeCheck;

    // Memory dump and stack dump
    private JTextArea memoryDumpArea;
    private JTextArea stackDumpArea;

    // Address viewer
    private JTextField addressField;
    private JLabel memoryValueLabel;

    // Memory dump field
    private JTextField memoryDumpField;
    private JLabel memoryDumpLabel;

    // We use AtomicBoolean to ensure that the running flag is updated atomically
    private AtomicBoolean running = new AtomicBoolean(false);
    private Thread runThread;

    // Speed slider
    private JSlider speedSlider;
    private JLabel speedValueLabel;

    public Visualizer(core.MOS6502 cpu) {
        super("6502 Debugger");
        this.cpu = cpu;

        initSystemLook();

        // buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton stepButton = new JButton("Step");
        JButton runButton  = new JButton("Run");
        JButton pauseButton = new JButton("Pause");
        JButton resetButton = new JButton("Reset");
        JButton loadProgramButton = new JButton("Load Program");
        JButton readAddrBtn = new JButton("Read");


        //speed slider, tells how fast the CPU should run
        speedSlider = new JSlider(10, 500, 100);
        speedValueLabel = new JLabel("Delay: 100 ms");

        topPanel.add(stepButton);
        topPanel.add(runButton);
        topPanel.add(pauseButton);
        topPanel.add(resetButton);
        topPanel.add(loadProgramButton);
        topPanel.add(speedValueLabel);
        topPanel.add(speedSlider);

        add(topPanel, BorderLayout.NORTH);

        // Main split pane
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setResizeWeight(0.3);
        add(mainSplit, BorderLayout.CENTER);

        // Left panel: registers and flags
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        mainSplit.setLeftComponent(leftPanel);

        leftPanel.add(RegistersPanel(), BorderLayout.NORTH);
        leftPanel.add(SpCyclesPanel(), BorderLayout.CENTER);
        leftPanel.add(FlagsPanel(), BorderLayout.SOUTH);

        // Right panel: memory dump, stack dump, address viewer
        JPanel rightPanel = new JPanel(new BorderLayout());
        mainSplit.setRightComponent(rightPanel);

        // create a vertical split pane for stack dump and memory dump
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        verticalSplit.setResizeWeight(0.25); // 25% height stack dump, 75% height memory dump

        // Add both scroll panes to the vertical split pane
        verticalSplit.setTopComponent(StackDumpPane());
        verticalSplit.setBottomComponent(MemoryDumpPane());

        // Add the vertical split pane to the right panel
        rightPanel.add(verticalSplit, BorderLayout.CENTER);

        // Address viewer and management
        rightPanel.add(AddrViewerJPanel(readAddrBtn), BorderLayout.SOUTH);

        createListeners(stackDumpArea, memoryDumpArea, memoryDumpField, stepButton, runButton, pauseButton, resetButton, loadProgramButton, readAddrBtn, speedSlider);
        updateUIState();    
        setLocationRelativeTo(null); 
    }

    private JScrollPane  StackDumpPane() {
        stackDumpArea = new JTextArea();
        stackDumpArea.setEditable(false);
        stackDumpArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane stackScrollPane = new JScrollPane(stackDumpArea);
        stackScrollPane.setBorder(BorderFactory.createTitledBorder("Stack Dump"));
        return stackScrollPane;
    }

    private JScrollPane  MemoryDumpPane() {
        memoryDumpArea = new JTextArea();
        memoryDumpArea.setEditable(false);
        memoryDumpArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane memoryScrollPane = new JScrollPane(memoryDumpArea);
        memoryScrollPane.setBorder(BorderFactory.createTitledBorder("Memory Dump"));
        return memoryScrollPane;
    }

    private JPanel SpCyclesPanel() {
        JPanel spCyclesPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        spCyclesPanel.setBorder(BorderFactory.createEmptyBorder(5,0,5,0));
        spCyclesPanel.add(new JLabel("SP:"));
        spCyclesPanel.add(spLabel);
        spCyclesPanel.add(new JLabel("Cycles:"));
        spCyclesPanel.add(cyclesLabel);
        return spCyclesPanel;
    }

    private JPanel RegistersPanel() {
        JPanel registersPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        registersPanel.setBorder(BorderFactory.createTitledBorder("Registers"));

        aLabel = new JLabel();
        xLabel = new JLabel();
        yLabel = new JLabel();
        pcLabel = new JLabel();
        spLabel = new JLabel();
        cyclesLabel = new JLabel();

        // Rejestry: A, X, Y, PC, SP, Cycles (wyświetlamy parami)
        registersPanel.add(new JLabel("A:"));
        registersPanel.add(aLabel);
        registersPanel.add(new JLabel("X:"));
        registersPanel.add(xLabel);
        registersPanel.add(new JLabel("Y:"));
        registersPanel.add(yLabel);
        registersPanel.add(new JLabel("PC:"));
        registersPanel.add(pcLabel);

        return registersPanel;
    }

    private JPanel FlagsPanel() {
        JPanel flagsPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        flagsPanel.setBorder(BorderFactory.createTitledBorder("Flags"));
        carryCheck     = createFlagCheck("Carry (C)");
        zeroCheck      = createFlagCheck("Zero (Z)");
        interruptCheck = createFlagCheck("Interrupt (I)");
        decimalCheck   = createFlagCheck("Decimal (D)");
        breakCheck     = createFlagCheck("Break (B)");
        unusedCheck    = createFlagCheck("Unused");
        overflowCheck  = createFlagCheck("Overflow (V)");
        negativeCheck  = createFlagCheck("Negative (N)");

        flagsPanel.add(carryCheck);
        flagsPanel.add(zeroCheck);
        flagsPanel.add(interruptCheck);
        flagsPanel.add(decimalCheck);
        flagsPanel.add(breakCheck);
        flagsPanel.add(unusedCheck);
        flagsPanel.add(overflowCheck);
        flagsPanel.add(negativeCheck);
        return flagsPanel;
    }

    private void initSystemLook() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLayout(new BorderLayout());
    }

    private JPanel AddrViewerJPanel(JButton readButton) {
        JPanel addrViewAndManagement = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addrViewAndManagement.setBorder(BorderFactory.createTitledBorder("Address Viewer and management"));

        addrViewAndManagement.add(new JLabel("Address (hex):"));
        addressField = new JTextField("8000", 6);
        addrViewAndManagement.add(addressField);
        addrViewAndManagement.add(readButton);
        memoryValueLabel = new JLabel("Value: 00");
        addrViewAndManagement.add(memoryValueLabel);
        addrViewAndManagement.add(new JLabel(" ")); // Spacer


        addrViewAndManagement.add(new JLabel("Memory dump (in bytes):"));
        memoryDumpField = new JTextField("256", 6);
        addrViewAndManagement.add(memoryDumpField);
        memoryDumpLabel = new JLabel("bytes");

        return addrViewAndManagement;
    }

    private void createListeners(JTextArea stackDumpArea, JTextArea memoryDumpArea, JTextField memoryDumpField, JButton stepButton, JButton runButton, JButton pauseButton, JButton resetButton, JButton loadProgramButton, JButton readButton, JSlider speedSlider) {
        
        // Button listeners
        stepButton.addActionListener(e -> doStep());
        runButton.addActionListener(e -> doRun());
        pauseButton.addActionListener(e -> doPause());
        resetButton.addActionListener(e -> doReset(cpu));

        loadProgramButton.addActionListener(e -> {
           //unimplemented, in the future i plan to have some kind of file chooser and text editor to load programs
           JOptionPane.showMessageDialog(
            this,
            "Feature not implemented yet!",
            "Information",
            JOptionPane.INFORMATION_MESSAGE
        );
    
        });

        readButton.addActionListener(e -> {
            try {
                int addr = Integer.parseInt(addressField.getText(), 16);
                int val = cpu.getMemory(addr);
                memoryValueLabel.setText(String.format("Value: %02X", val));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid address format!");
            }
        });

        // Listener for memory dump field
        memoryDumpField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                recalcDump();
            }
        
            @Override
            public void removeUpdate(DocumentEvent e) {
                recalcDump();
            }
        
            @Override
            public void changedUpdate(DocumentEvent e) {
                recalcDump();
            }
        
            private void recalcDump() {
                try {
                    int length = Integer.parseInt(memoryDumpField.getText());
                    if (length < 0 || length > 0x10000) {
                        throw new NumberFormatException();
                    }
                    memoryDumpArea.setText(dumpMemory(0x8000, length));
                } catch (NumberFormatException ex) {
                    memoryDumpArea.setText("");
                }
            }
        });

        memoryDumpArea.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                memoryDumpArea.setText(
                    dumpMemory(0x8000, Integer.parseInt(memoryDumpField.getText())) 
                );
                    
            }
        });

        stackDumpArea.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                stackDumpArea.setText(dumpStackMemory());
            }
        });

        speedSlider.addChangeListener((ChangeEvent e) -> {
            speedValueLabel.setText("Delay: " + speedSlider.getValue() + " ms");
        });
    }


    // the method creates a flag checkbox
    private JCheckBox createFlagCheck(String text) {
        JCheckBox check = new JCheckBox(text);
        check.setEnabled(false); // Flags are read-only
        return check;
    }

    // the method executes a single step of the CPU, and updates the UI state
    private void doStep() {
        cpu.step();
        updateUIState();
    }
 
    // the method starts the CPU running, and updates the UI state
    private void doRun() {
        if (running.get()) {
            return; 
        }
        running.set(true);
        runThread = new Thread(() -> {
            while (running.get()) {
                cpu.step(); // CPU step
                SwingUtilities.invokeLater(this::updateUIState);
                try {
                    Thread.sleep(speedSlider.getValue());
                } catch (InterruptedException e) {
                }
            }
        });
        runThread.start();
    }

    // the method pauses the CPU, and updates the UI state
    private void doPause() {
        running.set(false);
        if (runThread != null) {
            runThread.interrupt();
        }
    }

    // the method resets the CPU and memory, and updates the UI state
    private void doReset(core.MOS6502 cpu) {
        running.set(false);
        if (runThread != null) {
            runThread.interrupt();
        }
        cpu.reset();
        updateUIState();
    }

    // the method updates the UI state based on the CPU state
    private void updateUIState() {
        aLabel.setText(String.format("%02X", cpu.getAccumulator()));
        xLabel.setText(String.format("%02X", cpu.getX()));
        yLabel.setText(String.format("%02X", cpu.getY()));
        pcLabel.setText(String.format("%04X", cpu.getPC()));
        spLabel.setText(String.format("%02X", cpu.getS()));
        cyclesLabel.setText(String.valueOf(cpu.getCycles()));

        // Flags
        carryCheck.setSelected(cpu.getP() == (cpu.getP() | MOS6502.FLAG_CARRY));
        carryCheck.setSelected((cpu.getP() & MOS6502.FLAG_CARRY) != 0);
        zeroCheck.setSelected((cpu.getP() & MOS6502.FLAG_ZERO) != 0);
        interruptCheck.setSelected((cpu.getP() & MOS6502.FLAG_INTERRUPT) != 0);
        decimalCheck.setSelected((cpu.getP() & MOS6502.FLAG_DECIMAL) != 0);
        breakCheck.setSelected((cpu.getP() & MOS6502.FLAG_BREAK) != 0);
        unusedCheck.setSelected((cpu.getP() & MOS6502.FLAG_UNUSED) != 0);
        overflowCheck.setSelected((cpu.getP() & MOS6502.FLAG_OVERFLOW) != 0);
        negativeCheck.setSelected((cpu.getP() & MOS6502.FLAG_NEGATIVE) != 0);

        int length = Integer.parseInt(memoryDumpField.getText());
        memoryDumpArea.setText(dumpMemory(0x8000, length));
        stackDumpArea.setText(dumpStackMemory());
        highlightCurrentInstruction();
    }

    private int calculateSingleByteWidth() {
        return memoryDumpArea.getFontMetrics(memoryDumpArea.getFont()).charWidth('A') * 3;
    }

    private int calculateMemoryDumpAreaWidth() {
        int width = memoryDumpArea.getWidth();
        int charWidth = memoryDumpArea.getFontMetrics(memoryDumpArea.getFont()).charWidth('A');
        int bytesPerLine = width / (charWidth * 3);
        return bytesPerLine * calculateSingleByteWidth();
    }
    
    private int calculateNumberOfColumnsForMemoryDumpArea() {
        //20 is a magic number, it's a number of bytes that can fit in a single line
        int res = calculateMemoryDumpAreaWidth() / (calculateSingleByteWidth() * 20);
        if(res == 0){
            return 1;
        }
        return res;
    }


    private String dumpMemory(int startAddress, int length) {
        StringBuilder sb = new StringBuilder();
        int iterator = 0;
        String spacer = "     ";
        int cols = calculateNumberOfColumnsForMemoryDumpArea();
        for (int addr = startAddress; addr < startAddress + length; addr += 16) {
            iterator++;
            sb.append(String.format("%04X: ", addr));
            for (int i = 0; i < 16; i++) {
                if (addr + i < startAddress + length) {
                    int val = cpu.getMemory(addr + i);
                    sb.append(String.format("%02X ", val & 0xFF));
                }
            }
            if(iterator % cols == 0){
                sb.append("\n");
            } else {
                sb.append(spacer);
            }
        }
        return sb.toString();
    }

    private String dumpStackMemory(){
        return dumpMemory(0x0100, 0x100);
    }

private void highlightCurrentInstruction() {
    int pc = cpu.getPC(); // aktualna wartość Program Counter
    memoryDumpArea.getHighlighter().removeAllHighlights();

    try {
        int lineCount = memoryDumpArea.getLineCount();

        for (int i = 0; i < lineCount; i++) {
            int startOffset = memoryDumpArea.getLineStartOffset(i);
            int endOffset   = memoryDumpArea.getLineEndOffset(i);
            
            String line = memoryDumpArea.getText().substring(startOffset, endOffset).trim();
            if (line.length() < 5) {
                continue; 
            }

            String addressHex = line.substring(0, 4);
            int lineStartAddr = Integer.parseInt(addressHex, 16);

            if (pc >= lineStartAddr && pc <= (lineStartAddr + 15)) {
                int byteOffset = pc - lineStartAddr;

                int highlightStart = startOffset + 6 + (3 * byteOffset);

                int highlightEnd = highlightStart + 5;

                memoryDumpArea.getHighlighter().addHighlight(
                    highlightStart,
                    highlightEnd,
                    new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW)
                );

                break;
            }
        }
    } catch (BadLocationException | NumberFormatException e) {
        e.printStackTrace();
    };
}


    public static void main(String[] args) {
        core.MOS6502 cpu = new core.MOS6502();
        cpu.initInstructions();

        // Reset vector on $8000
        cpu.setMemory(0xFFFC, (byte)0x00);
        cpu.setMemory(0xFFFD, (byte)0x80);

        byte[] demoProgram = {
            (byte)0xA9, 0x0A,       // LDA #$0A   ; Załaduj do akumulatora value 0x0A (10)
            (byte)0x18,             // CLC        ; Wyczyść flagę przeniesienia (na wszelki wypadek)
            (byte)0x69, 0x05,       // ADC #$05   ; Dodaj 0x05 (5 dziesiętnie) do akumulatora 
            (byte)0x8D, (byte)0x00, (byte)0x02, // STA $0200; Zapisz value z akumulatora do pamięci pod adresem 0x0200
            (byte)0x00              // BRK        ; Zatrzymaj (Break)
        };

        cpu.loadProgram(demoProgram, 0x8000);
        cpu.reset();

        SwingUtilities.invokeLater(() -> {
            Visualizer debugger = new Visualizer(cpu);
            debugger.setVisible(true);
        });
    }
}
