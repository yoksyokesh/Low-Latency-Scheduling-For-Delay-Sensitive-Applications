package org.fog.placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;


public class MyModulePlacement extends ModulePlacement{
	
	protected ModuleMapping moduleMapping;
	protected List<Sensor> sensors;
	protected List<Actuator> actuators;
	protected String moduleToPlace;
	protected Map<Integer, Integer> deviceMipsInfo;
	
	
	public MyModulePlacement(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators, 
			Application application, ModuleMapping moduleMapping, String moduleToPlace){
		this.setFogDevices(fogDevices);
		this.setApplication(application);
		this.setModuleMapping(moduleMapping);
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		setSensors(sensors);
		setActuators(actuators);
		this.moduleToPlace = moduleToPlace;
		this.deviceMipsInfo = new HashMap<Integer, Integer>();
		mapModules();
	}

	private static double getvalue(double min, double max)
	{
		Random r = new Random();
		double randomValue = min + (max - min) * r.nextDouble();
		return randomValue;
	}
	
	private static int getvalue(int min, int max)
	{
		Random r = new Random();
		int randomValue = min + r.nextInt()%(max - min);
		if (randomValue<0) {
			randomValue = randomValue * (-1);
		}
		return randomValue;
	}
	
	@Override
	protected void mapModules() {
		
		
		for(String deviceName : getModuleMapping().getModuleMapping().keySet()){  
			for(String moduleName : getModuleMapping().getModuleMapping().get(deviceName)){ 
				int deviceId = CloudSim.getEntityId(deviceName); 
				AppModule appModule = getApplication().getModuleByName(moduleName); 
				
				if(!getDeviceToModuleMap().containsKey(deviceId)) 
				{ 
					List<AppModule>placedModules = new ArrayList<AppModule>(); 
					placedModules.add(appModule); 
					getDeviceToModuleMap().put(deviceId, placedModules); 
					
				}
				else
				{ 
					List<AppModule>placedModules = getDeviceToModuleMap().get(deviceId);
					placedModules.add(appModule);
					getDeviceToModuleMap().put(deviceId, placedModules); 
					
				}
				System.out.println("Module "+deviceName+" and "+moduleName);
			}
		}
		
		
		
		for(FogDevice device:getFogDevices()) 
		{
			int deviceParent = -1; 
			List<Integer>children = new ArrayList<Integer>();
			
			if(device.getLevel()==1) // 1.level fog node (4 and 14)
			{
				if(!deviceMipsInfo.containsKey(device.getId()))
					deviceMipsInfo.put(device.getId(), 0);
				deviceParent = device.getParentId();
				
				for(FogDevice deviceChild:getFogDevices())
				{
					if(deviceChild.getParentId()==device.getId()) 
					{
						children.add(deviceChild.getId()); 	
					}
					
				}
				
				System.out.println("Children: "+children); 
			
				Map<Integer, Integer>childDeadline = new HashMap<Integer, Integer>();
				
				for(int childId:children) {
					for(FogDevice deviceler:getFogDevices()) {
						if(childId == deviceler.getId()) {
							childDeadline.put(childId,deviceler.getMips());
						}
					}
				}
				
				System.out.println("Children deadline: "+childDeadline);
				
				// request
				List<Integer> requestMipsInfo = new ArrayList<Integer>();
				List<Integer> finishedRequestMipsInfo = new ArrayList<Integer>();
				List<Double> requestDeadlineInfo = new ArrayList<Double>();
				List<Double> finishedRequestDeadlineInfo = new ArrayList<Double>();
				
				for(int i=0;i<7;i++) {
					int requestMips = getvalue(1000, 1500);
					requestMipsInfo.add(requestMips);
					double requestDeadline = getvalue(2.0, 6.0);
					requestDeadlineInfo.add(requestDeadline);
				}
				
				System.out.println("Requests: "+requestMipsInfo+"\n");
				
				
				Map<Integer, Integer>sortedChildDeadline = new HashMap<Integer, Integer>(); // id->mips in fog 
				sortedChildDeadline = childDeadline.entrySet()
				        .stream()
				        .sorted(Map.Entry.comparingByValue())
				        .collect(Collectors.toMap(
				        Map.Entry::getKey, 
				        Map.Entry::getValue, 
				        (oldValue, newValue) -> oldValue, LinkedHashMap::new));
				
				System.out.println("Sorted fog device capacity"+sortedChildDeadline+"\n");
								
				List<Integer> keys = new ArrayList<Integer>(sortedChildDeadline.keySet());
				
				
				
				if(requestMipsInfo.get(0)<=sortedChildDeadline.get(keys.get(0))) {
					for(int i=0;i<requestMipsInfo.size();i++) {	
						for(int key:keys) {
							int currentMips = sortedChildDeadline.get(key);
							AppModule appModule = getApplication().getModuleByName(moduleToPlace);

							if (requestMipsInfo.get(i)<=currentMips) {
								currentMips = currentMips - requestMipsInfo.get(i);
								sortedChildDeadline.put(key, currentMips);
								System.out.println("The request  "+requestMipsInfo.get(i)+", is assigned to node with ID "+
										key+", for "+requestDeadlineInfo.get(i)+" units of time.");
								
								sortedChildDeadline = sortedChildDeadline.entrySet()
										.stream()
										.sorted(Map.Entry.comparingByValue())
										.collect(Collectors.toMap(
										Map.Entry::getKey, 
										Map.Entry::getValue, 
										(oldValue, newValue) -> oldValue, LinkedHashMap::new));
							
							System.out.println("Remaining sorted fog device capacity: "+sortedChildDeadline+"\n");
							
							if(!getDeviceToModuleMap().containsKey(key)){
								List<AppModule>placedModules = new ArrayList<AppModule>();
								placedModules.add(appModule);
								getDeviceToModuleMap().put(key, placedModules);
							}
							else{
								List<AppModule>placedModules = getDeviceToModuleMap().get(key);
								placedModules.add(appModule);
								getDeviceToModuleMap().put(key, placedModules);
							}
							
							finishedRequestMipsInfo.add(requestMipsInfo.get(i));
							finishedRequestDeadlineInfo.add(requestDeadlineInfo.get(i));
							requestMipsInfo.remove(i);
							requestDeadlineInfo.remove(i); i=0;
							break;
						}
					}
				}
				
				}
				
				
				
				if(requestMipsInfo.get(0)>sortedChildDeadline.get(keys.get(sortedChildDeadline.size()-1))) {
					int i=0;
					for(int request:requestMipsInfo) {
						ArrayList<Integer> sortedKeys = new ArrayList<Integer>(sortedChildDeadline.keySet());
						ArrayList<Integer> sortedValues = new ArrayList<Integer>(sortedChildDeadline.values());
					
						ArrayList<Integer> usedKeys = new ArrayList<Integer>(); // Keys used together
						ArrayList<Integer> usedValues = new ArrayList<Integer>(); // Values used together
						int sum = sortedValues.get(sortedValues.size()-1); // Values used together (Sum)
						usedKeys.add(sortedKeys.get(sortedKeys.size()-1));
						usedValues.add(sortedValues.get(sortedValues.size()-1));
					
						for(int k=sortedValues.size()-2; k>=0;k--){
							AppModule appModule = getApplication().getModuleByName(moduleToPlace);
							sum = sum + sortedValues.get(k);
							usedKeys.add(sortedKeys.get(k));
							usedValues.add(sortedValues.get(k));
							if (request<=sum) {
								int remain = request;
								for (int l=0; l<usedKeys.size();l++) {
									System.out.println("Part of the remaining request ("+remain+") is assigned to the node with ID "+
											usedKeys.get(l)+", for "+requestDeadlineInfo.get(i)+" units of time.");
									if(remain-usedValues.get(l)>=0) {
										remain=remain-usedValues.get(l);
										usedValues.set(l,0); 
									} else {
										int x = usedValues.get(l)-remain;
										usedValues.set(l,x);
									}
									sortedChildDeadline.put(usedKeys.get(l), usedValues.get(l));
									System.out.println("Remaining sorted fog device capacity: "+sortedChildDeadline+"\n");
								}
								
								sortedChildDeadline = sortedChildDeadline.entrySet()
										.stream()
										.sorted(Map.Entry.comparingByValue())
										.collect(Collectors.toMap(
											Map.Entry::getKey, 
											Map.Entry::getValue, 
											(oldValue, newValue) -> oldValue, LinkedHashMap::new));
								
								if(!getDeviceToModuleMap().containsKey(sortedKeys.get(k))){
									List<AppModule>placedModules = new ArrayList<AppModule>();
									placedModules.add(appModule);
									getDeviceToModuleMap().put(sortedKeys.get(k), placedModules);
								}
								else{
									
									List<AppModule>placedModules = getDeviceToModuleMap().get(sortedKeys.get(k));
									placedModules.add(appModule);
									getDeviceToModuleMap().put(sortedKeys.get(k), placedModules);
								}
								
								
								break;
							}
				        }
						
						
					
						
						if(request>sum) {
							AppModule appModule = getApplication().getModuleByName(moduleToPlace);
							List<AppModule> placedModules = getDeviceToModuleMap().get(deviceParent);
							placedModules.add(appModule);
							getDeviceToModuleMap().put(deviceParent, placedModules);
							System.out.println(" The request "+request+"is not assigned to any fog node.");
							System.out.println("getDeviceToModuleMap"+getDeviceToModuleMap());
							
						}
					
					i++;
					}
				}
	
			}
			
		}
		
	}
	
	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}


	public List<Sensor> getSensors() {
		return sensors;
	}

	public void setSensors(List<Sensor> sensors) {
		this.sensors = sensors;
	}

	public List<Actuator> getMyActuators() {
		return actuators;
	}

	public void setActuators(List<Actuator> actuators) {
		this.actuators = actuators;
	}

}
