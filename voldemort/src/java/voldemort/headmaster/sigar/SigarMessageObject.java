package voldemort.headmaster.sigar;

import java.io.Serializable;

public class SigarMessageObject implements Serializable{

    private double CPU;
    private double HDD;
    private double RAM;
    private String hostname;

    public SigarMessageObject(double CPU, double HDD, double RAM, String hostname){
        this.CPU = CPU;
        this.HDD = HDD;
        this.RAM = RAM;
        this.hostname = hostname;
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

    @Override
    public String toString() {
        return String.format("%s: CPU: %.2f RAM: %.2f HDD: %.2f", hostname,
                String.valueOf(CPU), String.valueOf(RAM), String.valueOf(HDD));
    }



}
