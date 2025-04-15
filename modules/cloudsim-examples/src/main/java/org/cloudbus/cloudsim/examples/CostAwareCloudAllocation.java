    package org.cloudbus.cloudsim.examples;

    import java.awt.BasicStroke;
    import java.awt.Color;
    import java.awt.Font;
    import java.io.File;
    import java.io.IOException;
    import java.util.ArrayList;
    import java.util.Comparator;
    import java.util.List;

    import org.cloudsimplus.allocationpolicies.VmAllocationPolicySimple;
    import org.cloudsimplus.brokers.DatacenterBroker;
    import org.cloudsimplus.brokers.DatacenterBrokerSimple;
    import org.cloudsimplus.cloudlets.Cloudlet;
    import org.cloudsimplus.cloudlets.CloudletSimple;
    import org.cloudsimplus.core.CloudSimPlus;
    import org.cloudsimplus.datacenters.Datacenter;
    import org.cloudsimplus.datacenters.DatacenterSimple;
    import org.cloudsimplus.hosts.Host;
    import org.cloudsimplus.hosts.HostSimple;
    import org.cloudsimplus.listeners.EventInfo;
    import org.cloudsimplus.listeners.EventListener;
    import org.cloudsimplus.power.models.PowerModelHostSimple;
    import org.cloudsimplus.resources.Pe;
    import org.cloudsimplus.resources.PeSimple;
    import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerTimeShared;
    import org.cloudsimplus.schedulers.vm.VmSchedulerTimeShared;
    import org.cloudsimplus.utilizationmodels.UtilizationModel;
    import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;
    import org.cloudsimplus.vms.Vm;
    import org.cloudsimplus.vms.VmSimple;
    import org.jfree.chart.ChartFactory;
    import org.jfree.chart.ChartUtils;
    import org.jfree.chart.JFreeChart;
    import org.jfree.chart.plot.XYPlot;
    import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
    import org.jfree.data.xy.XYSeries;
    import org.jfree.data.xy.XYSeriesCollection;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;

    public class CostAwareCloudAllocation {
        private static List<Double> powerOverTime = new ArrayList<>();
        private static List<Double> timeStamps = new ArrayList<>();
        private static List<Double> costOverTime = new ArrayList<>();
        private static final Logger logger = LoggerFactory.getLogger(CostAwareCloudAllocation.class);
        private static final int HOSTS = 8; // Increased host count for heterogeneity
        private static final int INITIAL_VMS = 5; // Start with fewer VMs
        private static final int CLOUDLETS = 8; // More cloudlets with varied arrival times
        private static final double SCHEDULING_INTERVAL = 1.0;
        private static double totalEnergyCost = 0.0;
        private static double totalSlaCost = 0.0;
        private static Datacenter datacenter;
        private static DatacenterBroker broker;

        public static void main(String[] args) {
            System.out.println("Starting Cost-Aware Cloud Resource Allocation Simulation...");
            logger.info("Simulation started for cost-aware resource allocation with dynamic workloads.");
            
            // 1. Initialize CloudSim Plus
            CloudSimPlus simulation = new CloudSimPlus();
            // 2. Create Datacenter with heterogeneous hosts
            datacenter = createHeterogeneousDatacenter(simulation);
            // 3. Create Broker
            broker = new DatacenterBrokerSimple(simulation);
            // 4. Create initial VMs
            List<Vm> vmList = createInitialVms();
            broker.submitVmList(vmList);
            // 5. Create dynamic cloudlets (some immediate, some delayed)
            createDynamicWorkloads(broker, vmList);
            
            // 6. Add event listeners for monitoring and auto-scaling
            simulation.addOnClockTickListener(createMonitoringListener());
            simulation.addOnClockTickListener(createAutoScalingListener());
            
            // 7. Start simulation
            simulation.start();
            
            // 8. Print results
            printResults(broker);
            generateCharts();
        }

        private static Datacenter createHeterogeneousDatacenter(CloudSimPlus simulation) {
            List<Host> hostList = new ArrayList<>();
    for (int i = 0; i < HOSTS; i++) {
        List<Pe> peList = new ArrayList<>();
        int pes = 4; // Number of Processing Elements (CPUs)
        long mips = 5000; // MIPS per CPU
        for (int j = 0; j < pes; j++) {
            peList.add(new PeSimple(mips));
        }

        double maxPower = 150; // Max power consumption
        double idlePower = 50; // Idle power consumption

        Host host = new HostSimple(262144, 160000000, 80000000, peList); // RAM = 256GB, BW = 160Gbps
        host.setVmScheduler(new VmSchedulerTimeShared());
        host.setPowerModel(new PowerModelHostSimple(maxPower, idlePower));
        hostList.add(host);
    }

    Datacenter dc = new DatacenterSimple(simulation, hostList, new VmAllocationPolicySimple());
    dc.setSchedulingInterval(SCHEDULING_INTERVAL);
    return dc;
}

        private static List<Vm> createInitialVms() {
            List<Vm> vmList = new ArrayList<>();
            for (int i = 0; i < INITIAL_VMS; i++) {
                Vm vm = new VmSimple(2000, 2); // MIPS = 2000, PEs = 2
                vm.setRam(32768).setBw(8000).setSize(20000); // RAM = 32GB, BW = 8000 Mbps
                vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
                vmList.add(vm);
            }
            return vmList;
        }

        private static void createDynamicWorkloads(DatacenterBroker broker, List<Vm> vmList) {
            // Create cloudlets with different arrival times and priorities
            for (int i = 0; i < CLOUDLETS; i++) {
                boolean highPriority = i % 5 == 0; // 20% are high priority
                long length = 10000 + (i * 500); // Varied lengths
                int pes = (i % 4 == 0) ? 2 : 1; // Some need multiple PEs
                
                // Create utilization models with different patterns
                UtilizationModel utilizationModel = new UtilizationModelDynamic(0.4 + (i % 3) * 0.2);
                
                Cloudlet cloudlet = new CloudletSimple(length, pes, utilizationModel);
                cloudlet.setSizes(1024); // Reduced from 2048
                cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(0.2)); // Reduced from 0.4
                cloudlet.setUtilizationModelBw(new UtilizationModelDynamic(0.2)); // Reduced from 0.3
                
                if (highPriority) {
                    cloudlet.setPriority(1); // Higher priority
                    cloudlet.setSubmissionDelay(i * 0.5); // Staggered arrival
                } else {
                    cloudlet.setPriority(2); // Normal priority
                    cloudlet.setSubmissionDelay(i * 0.2); // More frequent arrival
                }
                
                broker.submitCloudlet(cloudlet);
            }
        }

        private static EventListener<EventInfo> createMonitoringListener() {
            return eventInfo -> {
                double currentTime = eventInfo.getTime();
                if (currentTime > 0) {
                    // Calculate current power usage
                    double currentPower = datacenter.getHostList().stream()
                        .filter(host -> !host.isFailed())
                        .mapToDouble(host -> {
                            double utilization = host.getCpuPercentUtilization();
                            return host.getPowerModel().getPower(utilization);
                        })
                        .sum();
                    
                    // Calculate SLA violation cost (simple model)
                    double slaViolationCost = broker.getCloudletCreatedList().stream()
                        .filter(c -> c.getFinishTime() > 0 && c.getFinishTime() > c.getExecStartTime() + c.getLength()/c.getVm().getMips())
                        .count() * 0.10; // $0.10 penalty per violation
                    
                    // Update totals
                    totalEnergyCost += currentPower * SCHEDULING_INTERVAL / 3600.0 * 0.15; // $0.15 per Wh
                    totalSlaCost += slaViolationCost;
                    
                    // Record metrics
                    powerOverTime.add(currentPower);
                    costOverTime.add(currentPower * 0.15 + slaViolationCost);
                    timeStamps.add(currentTime);
                    
                    if (currentTime % 5 == 0) { // Log every 5 seconds
                        System.out.printf("\nTime: %.1f sec | Power: %.2f W | Current Cost: $%.2f (Energy: $%.2f, SLA: $%.2f)",
                            currentTime, currentPower, currentPower * 0.15 + slaViolationCost,
                            currentPower * 0.15, slaViolationCost);
                    }
                }
            };
        }

        private static EventListener<EventInfo> createAutoScalingListener() {
            return eventInfo -> {
                double currentTime = eventInfo.getTime();
                // Simple auto-scaling logic - add VMs if utilization is high
                if (currentTime > 0 && currentTime % 10 == 0) { // Check every 10 seconds
                    double avgUtilization = datacenter.getHostList().stream()
                        .filter(host -> !host.isFailed())
                        .mapToDouble(Host::getCpuPercentUtilization)
                        .average()
                        .orElse(0);
                    
                    if (avgUtilization > 0.7) { // Scale up if utilization > 70%
                        System.out.printf("\nHigh utilization (%.2f%%) - creating new VM", avgUtilization*100);
                        Vm newVm = new VmSimple(3000, 4);
                newVm.setRam(42768).setBw(18000).setSize(200000);
                        newVm.setCloudletScheduler(new CloudletSchedulerTimeShared());
                        broker.submitVm(newVm);
                    } else if (avgUtilization < 0.3 && broker.getVmCreatedList().size() > INITIAL_VMS) {
                        // Scale down if utilization < 30% (but keep initial VMs)
                        System.out.printf("\nLow utilization (%.2f%%) - destroying one VM", avgUtilization*100);
                        broker.getVmCreatedList().stream()
                            .filter(vm -> vm.getCloudletScheduler().getCloudletExecList().isEmpty())
                            .findFirst()
                            .ifPresent(vm -> broker.destroyVm(vm));
                    }
                }
            };
        }

        private static void generateCharts() {
            // Power usage chart
            XYSeries powerSeries = new XYSeries("Power Usage (W)");
            XYSeries costSeries = new XYSeries("Cost ($)");
            
            for (int i = 0; i < powerOverTime.size(); i++) {
                powerSeries.add(timeStamps.get(i), powerOverTime.get(i));
                costSeries.add(timeStamps.get(i), costOverTime.get(i));
            }
            
            // Create and save power chart
            JFreeChart powerChart = ChartFactory.createXYLineChart(
                "Power Usage Over Time",
                "Time (s)", "Power (W)",
                new XYSeriesCollection(powerSeries)
            );
            customizeChart(powerChart, Color.RED);
            saveChart(powerChart, "power_usage.png");
            
            // Create and save cost chart
            JFreeChart costChart = ChartFactory.createXYLineChart(
                "Operational Cost Over Time",
                "Time (s)", "Cost ($)",
                new XYSeriesCollection(costSeries)
            );
            customizeChart(costChart, Color.BLUE);
            saveChart(costChart, "operational_cost.png");
        }

        private static void customizeChart(JFreeChart chart, Color color) {
            XYPlot plot = chart.getXYPlot();
            XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
            renderer.setSeriesPaint(0, color);
            renderer.setSeriesStroke(0, new BasicStroke(2.0f));
            plot.setRenderer(renderer);
            chart.setAntiAlias(true);
            chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 14));
        }

        private static void saveChart(JFreeChart chart, String filename) {
            try {
                ChartUtils.saveChartAsPNG(new File(filename), chart, 800, 600);
                System.out.println("\nSaved chart: " + filename);
            } catch (IOException e) {
                System.err.println("Error saving chart: " + e.getMessage());
            }
        }

        private static void printResults(DatacenterBroker broker) {
            System.out.println("\n========== FINAL RESULTS ==========");
            System.out.printf("Total Energy Cost: $%.2f%n", totalEnergyCost);
            System.out.printf("Total SLA Violation Cost: $%.2f%n", totalSlaCost);
            System.out.printf("Total Operational Cost: $%.2f%n", totalEnergyCost + totalSlaCost);
            
            List<Cloudlet> finishedCloudlets = broker.getCloudletFinishedList();
            finishedCloudlets.sort(Comparator.comparingDouble(Cloudlet::getFinishTime));
            
            long violatedSlas = finishedCloudlets.stream()
                .filter(c -> c.getFinishTime() > c.getExecStartTime() + c.getLength()/c.getVm().getMips())
                .count();
                
            System.out.printf("\nSLA Metrics:\n  Total Cloudlets: %d\n  Violations: %d (%.1f%%)\n",
                finishedCloudlets.size(), violatedSlas,
                (double)violatedSlas/finishedCloudlets.size()*100);
                
            System.out.println("\nResource Utilization:");
            datacenter.getHostList().forEach(host -> {
                System.out.printf("Host %d: CPU %.1f%% | Power %.1fW%n",
                    host.getId(), host.getCpuPercentUtilization()*100,
                    host.getPowerModel().getPower(host.getCpuPercentUtilization()));
            });
        }
    }