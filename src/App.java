
import core.MOS6502;
import vis.Visualizer;

public class App {
    public static void main(String[] args)  {
        MOS6502 cpu = new MOS6502();
        Visualizer visualizer = new Visualizer(cpu);
        visualizer.visualize();
    }
}
