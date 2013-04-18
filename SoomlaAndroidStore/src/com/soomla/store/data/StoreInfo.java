/*
 * Copyright (C) 2012 Soomla Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.soomla.store.data;

import android.text.TextUtils;
import com.soomla.billing.util.AESObfuscator;
import com.soomla.store.IStoreAssets;
import com.soomla.store.StoreUtils;
import com.soomla.store.domain.*;
import com.soomla.store.domain.virtualCurrencies.VirtualCurrency;
import com.soomla.store.domain.virtualCurrencies.VirtualCurrencyPack;
import com.soomla.store.domain.virtualGoods.*;
import com.soomla.store.exceptions.VirtualItemNotFoundException;
import com.soomla.store.purchaseTypes.PurchaseType;
import com.soomla.store.purchaseTypes.PurchaseWithMarket;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * This class holds the store's meta data including:
 * - Virtual Currencies
 * - Virtual Currency Packs
 * - All kinds of Virtual Goods
 * - Virtual Categories
 * - NonConsumables
 */
public class StoreInfo {

    /**
     * This function initializes StoreInfo. On first initialization, when the
     * database doesn't have any previous version of the store metadata, StoreInfo
     * is being loaded from the given {@link IStoreAssets}. After the first initialization,
     * StoreInfo will be initialized from the database.
     *
     * IMPORTANT: If you want to override the current StoreInfo, you'll have to bump the version of your
     * implementation of IStoreAssets in order to remove the metadata when the application loads.
     * (bumping the version is done by returning a higher number in {@link com.soomla.store.IStoreAssets#getVersion()}.
     */
    public static void setStoreAssets(IStoreAssets storeAssets){
        if (storeAssets == null){
            StoreUtils.LogError(TAG, "The given store assets can't be null !");
            return;
        }

        // we prefer initialization from the database (storeAssets are only set on the first time the game is loaded)!
        if (!initializeFromDB()){

            initializeWithStoreAssets(storeAssets);
        }
    }

    /**
     * Initializes StoreInfo from the database. This action should be performed only once during the lifetime of
     * a session of the game. StoreController automatically initializes StoreInfo. Don't do it if you don't know what
     * you're doing.
     * @return success.
     */
    public static boolean initializeFromDB() {
        String key = KeyValDatabase.keyMetaStoreInfo();
        key = StorageManager.getAESObfuscator().obfuscateString(key);
        String val = StorageManager.getDatabase().getKeyVal(key);

        if (val == null && TextUtils.isEmpty(val)){
            StoreUtils.LogDebug(TAG, "store json is not in DB yet.");
            return false;
        }

        try {
            val = StorageManager.getAESObfuscator().unobfuscateToString(val);
        } catch (AESObfuscator.ValidationException e) {
            StoreUtils.LogError(TAG, e.getMessage());
            return false;
        }

        StoreUtils.LogDebug(TAG, "the metadata-economy json (from DB) is " + val);

        try {
            fromJSONObject(new JSONObject(val));

            // everything went well... StoreInfo is initialized from the local DB.
            // it's ok to return now.

            return true;
        } catch (JSONException e) {
            StoreUtils.LogDebug(TAG, "Can't parse metadata json. Going to return false and make " +
                    "StoreInfo load from static data.: " + val);
        }

        return false;

    }

    /**
     * A utility function to retrieve a single VirtualItem that resides in the meta data.
     * @param itemId the itemId of the required VirtualItem.
     * @throws VirtualItemNotFoundException when the given itemId was not found.
     */
    public static VirtualItem getVirtualItem(String itemId) throws VirtualItemNotFoundException{
        return mVirtualItems.get(itemId);
    }

    /**
     * A utility function to retrieve a single PurchasableVirtualItem that resides in the meta data.
     *
     * IMPORTANT: The retrieved PurchasableVirtualItems are only those which has a purchaseType of PurchaseWithMarket.
     * (This is why we fetch here with productId)
     *
     * @param productId the productId of the required PurchasableVirtualItem.
     * @throws VirtualItemNotFoundException when the given itemId was not found.
     */
    public static PurchasableVirtualItem getPurchasableItem(String productId) throws VirtualItemNotFoundException{
        return mPurchasableItems.get(productId);
    }

    /**
     * A utility function to retrieve a single VirtualCategory for a given VirtualGood itemId.
     *
     * @param goodItemId the virtualGood in the category.
     * @return a VirtualCategory for the given VirtualGood.
     */
    public static VirtualCategory getCategory(String goodItemId) {
        return mGoodsCategories.get(goodItemId);
    }

    /** Getters **/

    public static List<VirtualCurrency> getVirtualCurrencies(){
        return mCurrencies;
    }

    public static List<VirtualCurrencyPack> getCurrencyPacks() {
        return mCurrencyPacks;
    }

    public static List<VirtualGood> getVirtualGoods() {
        return mGoods;
    }

    public static List<NonConsumableItem> getNonConsumableItems() {
        return mNonConsumables;
    }

    /** Private functions **/

    private static void fromJSONObject(JSONObject jsonObject) throws JSONException{
        mVirtualItems = new HashMap<String, VirtualItem>();
        mPurchasableItems = new HashMap<String, PurchasableVirtualItem>();
        mGoodsCategories = new HashMap<String, VirtualCategory>();

        JSONArray virtualCurrencies = jsonObject.getJSONArray(JSONConsts.STORE_CURRENCIES);
        mCurrencies = new LinkedList<VirtualCurrency>();
        for (int i=0; i<virtualCurrencies.length(); i++){
            JSONObject o = virtualCurrencies.getJSONObject(i);
            VirtualCurrency c = new VirtualCurrency(o);
            mCurrencies.add(c);

            mVirtualItems.put(c.getItemId(), c);
        }

        JSONArray currencyPacks = jsonObject.getJSONArray(JSONConsts.STORE_CURRENCYPACKS);
        mCurrencyPacks = new LinkedList<VirtualCurrencyPack>();
        for (int i=0; i<currencyPacks.length(); i++){
            JSONObject o = currencyPacks.getJSONObject(i);
            VirtualCurrencyPack pack = new VirtualCurrencyPack(o);
            mCurrencyPacks.add(pack);

            mVirtualItems.put(pack.getItemId(), pack);

            PurchaseType purchaseType = pack.getPurchaseType();
            if (purchaseType instanceof PurchaseWithMarket) {
                mPurchasableItems.put(((PurchaseWithMarket) purchaseType).getGoogleMarketItem().getProductId(), pack);
            }
        }

        // The order in which VirtualGoods are created matters!
        // For example: VGU and VGP depend on other VGs
        JSONObject virtualGoods = jsonObject.getJSONObject(JSONConsts.STORE_GOODS);
        JSONArray suGoods = virtualGoods.getJSONArray(JSONConsts.STORE_GOODS_SU);
        JSONArray ltGoods = virtualGoods.getJSONArray(JSONConsts.STORE_GOODS_LT);
        JSONArray eqGoods = virtualGoods.getJSONArray(JSONConsts.STORE_GOODS_EQ);
        JSONArray upGoods = virtualGoods.getJSONArray(JSONConsts.STORE_GOODS_UP);
        JSONArray paGoods = virtualGoods.getJSONArray(JSONConsts.STORE_GOODS_PA);
        mGoods = new LinkedList<VirtualGood>();
        for (int i=0; i<suGoods.length(); i++){
            JSONObject o = suGoods.getJSONObject(i);
            SingleUseVG g = new SingleUseVG(o);
            addVG(g);
        }
        for (int i=0; i<ltGoods.length(); i++){
            JSONObject o = ltGoods.getJSONObject(i);
            LifetimeVG g = new LifetimeVG(o);
            addVG(g);
        }
        for (int i=0; i<eqGoods.length(); i++){
            JSONObject o = eqGoods.getJSONObject(i);
            EquippableVG g = new EquippableVG(o);
            addVG(g);
        }
        for (int i=0; i<paGoods.length(); i++){
            JSONObject o = paGoods.getJSONObject(i);
            SingleUsePackVG g = new SingleUsePackVG(o);
            addVG(g);
        }
        for (int i=0; i<upGoods.length(); i++){
            JSONObject o = upGoods.getJSONObject(i);
            UpgradeVG g = new UpgradeVG(o);
            addVG(g);
        }

        // Categories depend on virtual goods. That's why the have to be initialized after!
        JSONArray virtualCategories = jsonObject.getJSONArray(JSONConsts.STORE_CATEGORIES);
        mCategories = new LinkedList<VirtualCategory>();
        for(int i=0; i<virtualCategories.length(); i++){
            JSONObject o = virtualCategories.getJSONObject(i);
            VirtualCategory category = new VirtualCategory(o);
            mCategories.add(category);
            for(VirtualGood good : category.getGoods()) {
                mGoodsCategories.put(good.getItemId(), category);
            }
        }

        JSONArray nonConsumables = jsonObject.getJSONArray(JSONConsts.STORE_NONCONSUMABLES);
        mNonConsumables = new LinkedList<NonConsumableItem>();
        for (int i=0; i<nonConsumables.length(); i++){
            JSONObject o = nonConsumables.getJSONObject(i);
            NonConsumableItem non = new NonConsumableItem(o);
            mNonConsumables.add(non);

            mVirtualItems.put(non.getItemId(), non);

            PurchaseType purchaseType = non.getPurchaseType();
            if (purchaseType instanceof PurchaseWithMarket) {
                mPurchasableItems.put(((PurchaseWithMarket) purchaseType).getGoogleMarketItem().getProductId(), non);
            }
        }
    }

    private static void addVG(VirtualGood g) {
        mGoods.add(g);

        mVirtualItems.put(g.getItemId(), g);

        PurchaseType purchaseType = g.getPurchaseType();
        if (purchaseType instanceof PurchaseWithMarket) {
            mPurchasableItems.put(((PurchaseWithMarket) purchaseType).getGoogleMarketItem().getProductId(), g);
        }
    }

    /**
     * Converts StoreInfo to a JSONObject.
     * @return a JSONObject representation of the StoreInfo.
     */
    public static JSONObject toJSONObject(){

        JSONArray currencies = new JSONArray();
        for(VirtualCurrency c : mCurrencies){
            currencies.put(c.toJSONObject());
        }

        JSONArray currencyPacks = new JSONArray();
        for(VirtualCurrencyPack pack : mCurrencyPacks){
            currencyPacks.put(pack.toJSONObject());
        }

        JSONObject goods = new JSONObject();
        JSONArray suGoods = new JSONArray();
        JSONArray ltGoods = new JSONArray();
        JSONArray eqGoods = new JSONArray();
        JSONArray paGoods = new JSONArray();
        JSONArray upGoods = new JSONArray();
        for(VirtualGood good : mGoods){
            if (good instanceof SingleUseVG) {
                suGoods.put(good.toJSONObject());
            }  else if (good instanceof EquippableVG) {
                eqGoods.put(good.toJSONObject());
            } else if (good instanceof LifetimeVG) {
                ltGoods.put(good.toJSONObject());
            } else if (good instanceof SingleUsePackVG) {
                paGoods.put(good.toJSONObject());
            } else if (good instanceof UpgradeVG) {
                upGoods.put(good.toJSONObject());
            }
        }


        JSONArray categories = new JSONArray();
        for (VirtualCategory cat : mCategories){
            categories.put(cat.toJSONObject());
        }

        JSONArray nonConsumableItems = new JSONArray();
        for(NonConsumableItem non : mNonConsumables){
            nonConsumableItems.put(non.toJSONObject());
        }

        JSONObject jsonObject = new JSONObject();
        try {
            goods.put(JSONConsts.STORE_GOODS_SU, suGoods);
            goods.put(JSONConsts.STORE_GOODS_LT, ltGoods);
            goods.put(JSONConsts.STORE_GOODS_EQ, eqGoods);
            goods.put(JSONConsts.STORE_GOODS_PA, paGoods);
            goods.put(JSONConsts.STORE_GOODS_UP, upGoods);

            jsonObject.put(JSONConsts.STORE_CATEGORIES, categories);
            jsonObject.put(JSONConsts.STORE_CURRENCIES, currencies);
            jsonObject.put(JSONConsts.STORE_GOODS, goods);
            jsonObject.put(JSONConsts.STORE_CURRENCYPACKS, currencyPacks);
            jsonObject.put(JSONConsts.STORE_NONCONSUMABLES, nonConsumableItems);
        } catch (JSONException e) {
            StoreUtils.LogDebug(TAG, "An error occurred while generating JSON object.");
        }

        return jsonObject;
    }

    private static void initializeWithStoreAssets(IStoreAssets storeAssets) {
        /// fall-back here if the json doesn't exist, we load the store from the given {@link IStoreAssets}.
        mCurrencies = Arrays.asList(storeAssets.getVirtualCurrencies());
        mCurrencyPacks = Arrays.asList(storeAssets.getVirtualCurrencyPacks());
        mGoods = Arrays.asList(storeAssets.getVirtualGoods());
        mCategories = Arrays.asList(storeAssets.getVirtualCategories());
        mNonConsumables = Arrays.asList(storeAssets.getNonConsumableItems());

        mVirtualItems = new HashMap<String, VirtualItem>();
        mPurchasableItems = new HashMap<String, PurchasableVirtualItem>();
        mGoodsCategories = new HashMap<String, VirtualCategory>();

        for(VirtualCurrency vi : mCurrencies) {
            mVirtualItems.put(vi.getItemId(), vi);
        }

        for(VirtualCurrencyPack vi : mCurrencyPacks) {
            mVirtualItems.put(vi.getItemId(), vi);

            PurchaseType purchaseType = vi.getPurchaseType();
            if (purchaseType instanceof PurchaseWithMarket) {
                mPurchasableItems.put(((PurchaseWithMarket) purchaseType).getGoogleMarketItem().getProductId(), vi);
            }
        }

        for(VirtualGood vi : mGoods) {
            mVirtualItems.put(vi.getItemId(), vi);

            PurchaseType purchaseType = vi.getPurchaseType();
            if (purchaseType instanceof PurchaseWithMarket) {
                mPurchasableItems.put(((PurchaseWithMarket) purchaseType).getGoogleMarketItem().getProductId(), vi);
            }
        }

        for(NonConsumableItem vi : mNonConsumables) {
            mVirtualItems.put(vi.getItemId(), vi);

            PurchaseType purchaseType = vi.getPurchaseType();
            if (purchaseType instanceof PurchaseWithMarket) {
                mPurchasableItems.put(((PurchaseWithMarket) purchaseType).getGoogleMarketItem().getProductId(), vi);
            }
        }

        for(VirtualCategory category : mCategories) {
            for(VirtualGood good : category.getGoods()) {
                mGoodsCategories.put(good.getItemId(), category);
            }
        }

        // put StoreInfo in the database as JSON
        String store_json = toJSONObject().toString();
        String key = KeyValDatabase.keyMetaStoreInfo();
        store_json = StorageManager.getAESObfuscator().obfuscateString(store_json);
        key = StorageManager.getAESObfuscator().obfuscateString(key);
        StorageManager.getDatabase().setKeyVal(key, store_json);
    }

    /** Private members **/

    private static final String TAG = "SOOMLA StoreInfo";

    // convenient hash to retrieve virtual items
    private static HashMap<String, VirtualItem>             mVirtualItems;
    private static HashMap<String, PurchasableVirtualItem>  mPurchasableItems;
    private static HashMap<String, VirtualCategory>         mGoodsCategories;

    private static List<VirtualCurrency>                mCurrencies;
    private static List<VirtualCurrencyPack>            mCurrencyPacks;
    private static List<VirtualGood>                    mGoods;
    private static List<VirtualCategory>                mCategories;
    private static List<NonConsumableItem>              mNonConsumables;
}