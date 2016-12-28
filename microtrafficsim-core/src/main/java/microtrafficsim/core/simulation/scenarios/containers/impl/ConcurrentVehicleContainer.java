package microtrafficsim.core.simulation.scenarios.containers.impl;

import microtrafficsim.core.entities.vehicle.VisualizationVehicleEntity;
import microtrafficsim.core.logic.vehicles.AbstractVehicle;
import microtrafficsim.core.logic.vehicles.VehicleState;
import microtrafficsim.core.simulation.scenarios.containers.VehicleContainer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


/**
 * This implementation of {@code VehicleContainer} uses a few sets for managing the vehicles. All methods are
 * synchronized.
 *
 * @author Dominic Parga Cacheiro
 */
public class ConcurrentVehicleContainer implements VehicleContainer {

    protected Supplier<VisualizationVehicleEntity> vehicleFactory;
    protected Set<AbstractVehicle>                 spawnedVehicles, notSpawnedVehicles, vehicles;

    /**
     * Default constructor. It initializes the used sets as concurrent ones, so they can be edited while iterated.
     *
     * @param vehicleFactory This factory is needed to create the vehicles' visualization components.
     */
    public ConcurrentVehicleContainer(Supplier<VisualizationVehicleEntity> vehicleFactory) {
        spawnedVehicles     = new HashSet<>();
        notSpawnedVehicles  = new HashSet<>();
        vehicles            = new HashSet<>();
        this.vehicleFactory = vehicleFactory;
    }

    /*
    |======================|
    | (i) VehicleContainer |
    |======================|
    */
    @Override
    public synchronized void addVehicle(AbstractVehicle vehicle) {
        notSpawnedVehicles.add(vehicle);
        vehicles.add(vehicle);
    }

    @Override
    public synchronized void clearAll() {
        spawnedVehicles.clear();
        notSpawnedVehicles.clear();
        vehicles.clear();
    }

    @Override
    public synchronized int getVehicleCount() {
        return vehicles.size();
    }

    @Override
    public synchronized int getSpawnedCount() {
        return spawnedVehicles.size();
    }

    @Override
    public synchronized int getNotSpawnedCount() {
        return notSpawnedVehicles.size();
    }

    /**
     * Addition to superclass: Due to concurrency, this method returns a shallow copy created synchronized.
     */
    @Override
    public synchronized Set<AbstractVehicle> getVehicles() {
        return new HashSet<>(vehicles);
    }

    /**
     * Addition to superclass: Due to concurrency, this method returns a shallow copy created synchronized.
     */
    @Override
    public synchronized Set<AbstractVehicle> getSpawnedVehicles() {
        return new HashSet<>(vehicles);
    }

    /**
     * Addition to superclass: Due to concurrency, this method returns a shallow copy created synchronized.
     */
    @Override
    public synchronized Set<AbstractVehicle> getNotSpawnedVehicles() {
        return new HashSet<>(vehicles);
    }

    /*
    |==========================|
    | (i) VehicleStateListener |
    |==========================|
    */
    @Override
    public synchronized void stateChanged(AbstractVehicle vehicle) {
        if (vehicle.getState() == VehicleState.DESPAWNED) {
            spawnedVehicles.remove(vehicle);
            notSpawnedVehicles.remove(vehicle);
            vehicles.remove(vehicle);
        } else if (vehicle.getState() == VehicleState.SPAWNED) {
            notSpawnedVehicles.remove(vehicle);
            spawnedVehicles.add(vehicle);
        }
    }
}