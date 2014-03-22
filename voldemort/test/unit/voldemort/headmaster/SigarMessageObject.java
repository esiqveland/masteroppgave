package voldemort.headmaster;

public class SigarMessageObject {

    private double CPU;
    private double HDD;
    private double RAM;
    private String hostname;

    public SigarMessageObject(double CPU, double HDD, double RAM){
        this.CPU = CPU;
        this.HDD = HDD;
        this.RAM = RAM;
    }

    public double getCPU() {
        return CPU;
    }

    public double getHDD() {
        return HDD;
    }

    public double getRAM() {
        return RAM;
    }

    public String getHostname() {
        return hostname;
    }




}
