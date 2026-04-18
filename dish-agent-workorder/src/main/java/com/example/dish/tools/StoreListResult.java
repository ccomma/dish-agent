package com.example.dish.tools;

import java.util.List;

/**
 * 门店列表结果
 */
public class StoreListResult {

    private final List<StoreInfo> stores;

    public StoreListResult(List<StoreInfo> stores) {
        this.stores = stores;
    }

    public List<StoreInfo> getStores() { return stores; }

    public static class StoreInfo {
        private final String storeId;
        private final String name;
        private final String address;

        public StoreInfo(String storeId, String name, String address) {
            this.storeId = storeId;
            this.name = name;
            this.address = address;
        }

        public String getStoreId() { return storeId; }
        public String getName() { return name; }
        public String getAddress() { return address; }
    }
}
