package com.jobhunter.shared.domain;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/** MySQL-specific functions usable from JPA criteria (registered via META-INF/services). */
public class MySqlFunctions implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        var registry = contributions.getFunctionRegistry();
        var types = contributions.getTypeConfiguration().getBasicTypeRegistry();
        registry.registerPattern("match_against", "match (?1, ?2) against (?3 in boolean mode)",
                types.resolve(StandardBasicTypes.DOUBLE));
        registry.registerPattern("json_overlaps", "json_overlaps(?1, ?2)", types.resolve(StandardBasicTypes.INTEGER));
    }
}
