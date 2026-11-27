package com.jobhunter.jobsources.application;

import com.jobhunter.jobsources.domain.JobSourceConnector;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ConnectorRegistry {

    private final Map<String, JobSourceConnector> connectors;

    public ConnectorRegistry(List<JobSourceConnector> connectors) {
        this.connectors = connectors.stream().collect(Collectors.toMap(JobSourceConnector::sourceCode, Function.identity()));
    }

    public Optional<JobSourceConnector> get(String sourceCode) {
        return Optional.ofNullable(connectors.get(sourceCode));
    }

    public Map<String, JobSourceConnector> all() {
        return connectors;
    }
}
