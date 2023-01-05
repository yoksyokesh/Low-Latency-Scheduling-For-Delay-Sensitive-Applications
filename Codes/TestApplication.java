package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
//import java.util.Random;

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
import org.fog.entities.FogBroker;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.ModuleMapping;
//import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.Controller;
import org.fog.placement.MyModulePlacement;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;


public class TestApplication {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static Map<Integer,FogDevice> deviceById = new HashMap<Integer,FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	static List<Integer> idOfEndDevices = new ArrayList<Integer>();
	static List<Integer> MipsOfEndDevices = new ArrayList<Integer>();
	static Map<String, Double> deadlineInfo = new HashMap<String, Double>();
	static Map<String, Integer> additionalMipsInfo = new HashMap<String, Integer>();
	static Map<Integer, Map<String, Integer>> fogIdMips = new HashMap<Integer, Map<String, Integer>>();
	
	static boolean CLOUD = false;
	
	static int numOfGateways = 1;
	static int numOfEndDevPerGateway = 2;
	static double sensingInterval = 5; 
	
	public static void main(String[] args) {

		Log.printLine("Knapsack Algorithm ...");

		try {
			Log.disable();
			int num_user = 1;
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; 
			CloudSim.init(num_user, calendar, trace_flag);
			String appId = "test_app"; 
			FogBroker broker = new FogBroker("broker");
			
			createFogDevices(broker.getId(), appId);
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); 
			
			moduleMapping.addModuleToDevice("storageModule", "cloud"); 
			for(int i=0;i<idOfEndDevices.size();i++)
			{
				FogDevice fogDevice = deviceById.get(idOfEndDevices.get(i));
				moduleMapping.addModuleToDevice("clientModule", fogDevice.getName()); 
				
			}
			
			
			Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
			
			controller.submitApplication(application, 0, new MyModulePlacement(fogDevices, sensors, actuators, application, moduleMapping, "mainModule"));

			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("TestApplication finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	


	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		deviceById.put(cloud.getId(), cloud);
		
		for(int i=0;i<numOfGateways;i++){
			addGw(i+"", userId, appId, cloud.getId()); 
		}
		
	}

	private static void addGw(String gwPartialName, int userId, String appId, int parentId){
		FogDevice gw = createFogDevice("g-"+gwPartialName, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
		fogDevices.add(gw);
		deviceById.put(gw.getId(), gw);
		gw.setParentId(parentId);
		gw.setUplinkLatency(4); 
		for(int i=0;i<numOfEndDevPerGateway;i++){
			String endPartialName = gwPartialName+"-"+i;
			FogDevice end  = addEnd(endPartialName, userId, appId, gw.getId()); 
			end.setUplinkLatency(2); 
			fogDevices.add(end);
			deviceById.put(end.getId(), end);
		}
		
	}
	
	private static FogDevice addEnd(String endPartialName, int userId, String appId, int parentId){
		FogDevice end = createFogDevice("e-"+endPartialName, 3200, 1000, 10000, 270, 2, 0, 87.53, 82.44);
		end.setParentId(parentId);
		idOfEndDevices.add(end.getId());
		MipsOfEndDevices.add(end.getMips());
		Sensor sensor = new Sensor("s-"+endPartialName, "IoTSensor", userId, appId, new DeterministicDistribution(sensingInterval)); 
		sensors.add(sensor);
		Actuator actuator = new Actuator("a-"+endPartialName, userId, appId, "IoTActuator");
		actuators.add(actuator);
		sensor.setGatewayDeviceId(end.getId());
		sensor.setLatency(6.0);
		actuator.setGatewayDeviceId(end.getId());
		actuator.setLatency(1.0);
		return end;
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
		fogdevice.setMips((int) mips);
		return fogdevice;
	}

	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId); 
		application.addAppModule("clientModule",10, 1000, 1000, 100); 
		application.addAppModule("mainModule", 50, 1500, 4000, 800); 
		application.addAppModule("storageModule", 10, 50, 12000, 100); 
		
		application.addAppEdge("IoTSensor", "clientModule", 100, 200, "IoTSensor", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("clientModule", "mainModule", 6000, 600  , "RawData", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("mainModule", "storageModule", 1000, 300, "StoreData", Tuple.UP, AppEdge.MODULE); 
		application.addAppEdge("mainModule", "clientModule", 100, 50, "ResultData", Tuple.DOWN, AppEdge.MODULE); 
		application.addAppEdge("clientModule", "IoTActuator", 100, 50, "Response", Tuple.DOWN, AppEdge.ACTUATOR); 
		
		application.addTupleMapping("clientModule", "IoTSensor", "RawData", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("mainModule", "RawData", "ResultData", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("mainModule", "RawData", "StoreData", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("clientModule", "ResultData", "Response", new FractionalSelectivity(1.0)); 
		
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("IoTSensor");add("clientModule");add("mainModule");add("clientModule");add("IoTActuator");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		application.setLoops(loops);

		return application;
	}
}