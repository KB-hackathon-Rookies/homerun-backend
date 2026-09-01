package com.homerun.global.external.building;

import org.springframework.stereotype.Service;

@Service
public class BuildingLedgerService {

    private final BuildingRegisterClient client;

    public BuildingLedgerService(BuildingRegisterClient client) {
        this.client = client;
    }

    public BuildingLedgerResponse findLedger(BuildingLotQuery query) {
        return new BuildingLedgerResponse(client.findTitles(query), client.findHousingPrices(query));
    }
}
