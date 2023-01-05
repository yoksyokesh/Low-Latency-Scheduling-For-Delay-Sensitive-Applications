package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.*;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.placement.ModulePlacementMobileEdgewards;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;


public class threshold {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	
	static List<FogDevice> highFog = new ArrayList<FogDevice>();
	static List<FogDevice> lowFog = new ArrayList<FogDevice>();
	static List<Sensor> highSensor = new ArrayList<Sensor>();
	static List<Sensor> lowSensor = new ArrayList<Sensor>();
	static List<Actuator> highActuator = new ArrayList<Actuator>();
	static List<Actuator> lowActuator = new ArrayList<Actuator>();
	
	static List<FogDevice> tFog = new ArrayList<FogDevice>();
	static List<Sensor> tSensor = new ArrayList<Sensor>();
	static List<Actuator> tActuator = new ArrayList<Actuator>();
	static List<FogDevice> pre = new ArrayList<FogDevice>();
	
	static List<Integer> thre = new ArrayList<Integer>();
	
	static int thresholdValue = 1000;
	
	static boolean CLOUD = false;
	
	static int numOfDepts = 2;
	static int numOfMobilesPerDept = 4;
	static double EEG_TRANSMISSION_TIME = 5;
	
	static int rand =0;
	
	public static void main(String[] args) {

		Log.printLine("Prioritising using a Threshold value...");

		try {
			Log.disable();
			int num_user = 1; 
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; 

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "vr_game"; 
			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			createFogDevices(broker.getId(), appId);
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); 
			
			if(CLOUD){
				
				moduleMapping.addModuleToDevice("connector", "cloud");
				moduleMapping.addModuleToDevice("concentration_calculator", "cloud"); 
				for(FogDevice device : fogDevices){
					if(device.getName().startsWith("m")){
						
						moduleMapping.addModuleToDevice("client", device.getName()); 
					}
				}
			}else{
				
				moduleMapping.addModuleToDevice("connector", "cloud"); 
			}
			
			
			tFog.addAll(highFog); tFog.addAll(lowFog);
			tSensor.addAll(highSensor); tSensor.addAll(lowSensor);
			tActuator.addAll(highActuator); tActuator.addAll(lowActuator);
			pre.addAll(tFog);
			
			Controller controller = new Controller("master-controller", pre, sensors, actuators);
			controller.submitApplication(application,0,
					(CLOUD)?(new ModulePlacementMapping(pre,application,moduleMapping))
							:(new ModulePlacementEdgewards(pre, sensors, actuators, application, moduleMapping)));
			
			Log.enable();
			Log.printLine(thre);
			Log.disable();
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

			CloudSim.startSimulation();

			CloudSim.stopSimulation();
			
			Log.printLine("VRGame finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}

	
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25); 
		cloud.setParentId(-1);
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333); 
		proxy.setParentId(cloud.getId()); 
		proxy.setUplinkLatency(100); 
		
		fogDevices.add(cloud);
		fogDevices.add(proxy);
		
		pre.add(cloud);
		pre.add(proxy);
		
		for(int i=0;i<numOfDepts;i++){
			addGw(i+"", userId, appId, proxy.getId()); 
		}
		
	}

	private static FogDevice addGw(String id, int userId, String appId, int parentId){
		FogDevice dept = createFogDevice("d-"+id, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
		fogDevices.add(dept);
		pre.add(dept);
		dept.setParentId(parentId);
		dept.setUplinkLatency(4); 
		int max = 2000;
        int min = 100;
        int range = max - min + 1;
		for(int i=0;i<numOfMobilesPerDept;i++){
			
			int rand = (int)(Math.random() * range) + min;
			thre.add(rand);
			String mobileId = id+"-"+i;
			FogDevice mobile = addMobile(mobileId, userId, appId, dept.getId());
			mobile.setUplinkLatency(2); 
			fogDevices.add(mobile);
			

			if(rand<=thresholdValue)
				highFog.add(mobile);
			else
				lowFog.add(mobile);
		}
		return dept;
	}
	
	private static FogDevice addMobile(String id, int userId, String appId, int parentId){
		FogDevice mobile = createFogDevice("m-"+id, 1000, 1000, 10000, 270, 3, 0, 87.53, 82.44);
		mobile.setParentId(parentId);
		
		Sensor eegSensor = new Sensor("s-"+id, "EEG", userId, appId, new DeterministicDistribution(EEG_TRANSMISSION_TIME)); 
		sensors.add(eegSensor);
		Actuator display = new Actuator("a-"+id, userId, appId, "DISPLAY");
		actuators.add(display);
		
		eegSensor.setGatewayDeviceId(mobile.getId());
		eegSensor.setLatency(6.0);  
		display.setGatewayDeviceId(mobile.getId());
		display.setLatency(1.0);  
		return mobile;
	}
	
	
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); 

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; 
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; 
		String os = "Linux"; 
		String vmm = "Xen";
		double time_zone = 10.0; 
		double cost = 3.0; 
		double costPerMem = 0.05;
		double costPerStorage = 0.001; 
										
		double costPerBw = 0.0; 
		LinkedList<Storage> storageList = new LinkedList<Storage>();
													

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId); 
		
		
		application.addAppModule("client", 10); 
		application.addAppModule("concentration_calculator", 10); 
		application.addAppModule("connector", 10); 
		
		
		 
		if(EEG_TRANSMISSION_TIME==10)
			application.addAppEdge("EEG", "client", 2000, 500, "EEG", Tuple.UP, AppEdge.SENSOR); 
		else
			application.addAppEdge("EEG", "client", 3000, 500, "EEG", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("client", "concentration_calculator", 3500, 500, "_SENSOR", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("concentration_calculator", "connector", 100, 1000, 1000, "PLAYER_GAME_STATE", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("concentration_calculator", "client", 14, 500, "CONCENTRATION", Tuple.DOWN, AppEdge.MODULE);  
		application.addAppEdge("connector", "client", 100, 28, 1000, "GLOBAL_GAME_STATE", Tuple.DOWN, AppEdge.MODULE); 
		application.addAppEdge("client", "DISPLAY", 1000, 500, "SELF_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR);  
		application.addAppEdge("client", "DISPLAY", 1000, 500, "GLOBAL_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR);  
		
		
		application.addTupleMapping("client", "EEG", "_SENSOR", new FractionalSelectivity(0.9)); 
		application.addTupleMapping("client", "CONCENTRATION", "SELF_STATE_UPDATE", new FractionalSelectivity(1.0));
		application.addTupleMapping("concentration_calculator", "_SENSOR", "CONCENTRATION", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("client", "GLOBAL_GAME_STATE", "GLOBAL_STATE_UPDATE", new FractionalSelectivity(1.0)); 
	
		
		
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("EEG");add("client");add("concentration_calculator");add("client");add("DISPLAY");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		application.setLoops(loops);
		
		return application;
	}
}