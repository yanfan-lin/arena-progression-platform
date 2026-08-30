package com.yanfan.arena.simulator.simulation;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.simulator.simulation.match.MatchSimulationService;
import com.yanfan.arena.simulator.simulation.run.SimulationRunRequest;
import com.yanfan.arena.simulator.simulation.run.SimulationRunResponse;
import com.yanfan.arena.simulator.simulation.run.SimulationRunService;
import com.yanfan.arena.simulator.simulation.setup.SimulationSetupRequest;
import com.yanfan.arena.simulator.simulation.setup.SimulationSetupResponse;
import com.yanfan.arena.simulator.simulation.setup.SimulationSetupService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Expose simulation controls through REST
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    // Prepare teams and rosters for the match simulation
    private final SimulationSetupService simulationSetupService;

    // Generate matches and publish them to Kafka
    private final MatchSimulationService matchSimulationService;

    private final SimulationRunService simulationRunService;

    @Autowired
    public SimulationController(SimulationSetupService simulationSetupService,
                                MatchSimulationService matchSimulationService,
                                SimulationRunService simulationRunService)
    {
        this.simulationSetupService = simulationSetupService;
        this.matchSimulationService = matchSimulationService;
        this.simulationRunService = simulationRunService;
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

    @PostMapping("/runs")
    public ResponseEntity<SimulationRunResponse> startRun(
            @Valid @RequestBody SimulationRunRequest request)
    {
        return ResponseEntity.accepted()
                .body(simulationRunService.startRun(request));
    }

    @GetMapping("/runs/current")
    public SimulationRunResponse getCurrentRun()  {
        return simulationRunService.getCurrentRun();
    }

    @DeleteMapping("/runs/current")
    public SimulationRunResponse stopRun() {
        return simulationRunService.stopCurrentRun();
    }


}
