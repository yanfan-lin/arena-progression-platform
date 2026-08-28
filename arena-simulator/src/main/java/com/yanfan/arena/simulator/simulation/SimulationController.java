package com.yanfan.arena.simulator.simulation;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Expose simulation controls through REST
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final SimulationSetupService simulationSetupService;

    @Autowired
    public SimulationController(SimulationSetupService simulationSetupService) {
        this.simulationSetupService = simulationSetupService;
    }

    @PostMapping("/setup")
    public SimulationSetupResponse setupSimulation(
            @Valid @RequestBody SimulationSetupRequest request)
    {
        return simulationSetupService.setup(request);
    }

}
