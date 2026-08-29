package com.yanfan.arena.simulator.simulation;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

// Expose simulation controls through REST
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    // Prepare teams and rosters for the match simulation
    private final SimulationSetupService simulationSetupService;

    // Generate matches and publish them to Kafka
    private final MatchSimulationService matchSimulationService;

    @Autowired
    public SimulationController(SimulationSetupService simulationSetupService,
                                MatchSimulationService matchSimulationService)
    {
        this.simulationSetupService = simulationSetupService;
        this.matchSimulationService = matchSimulationService;
    }

    @PostMapping("/setup")
    public SimulationSetupResponse setupSimulation(
            @Valid @RequestBody SimulationSetupRequest request)
    {
        return simulationSetupService.setup(request);
    }

    @PostMapping("/matches")
    public ArenaMatchCompleted simulateMatch(@RequestParam MatchMode mode) {

        return matchSimulationService.simulateMatch(mode);
    }

}
