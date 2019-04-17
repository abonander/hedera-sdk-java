package com.hedera.sdk.examples.advanced;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hedera.sdk.HederaException;
import com.hedera.sdk.contract.ContractCallQuery;
import com.hedera.sdk.contract.ContractCreateTransaction;
import com.hedera.sdk.contract.ContractExecuteTransaction;
import com.hedera.sdk.examples.ExampleHelper;
import com.hedera.sdk.file.FileCreateTransaction;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class CreateStatefulContract {
    public static void main(String[] args) throws HederaException, IOException {
        var cl = CreateStatefulContract.class.getClassLoader();

        var gson = new Gson();

        JsonObject jsonObject;

        try (var jsonStream = cl.getResourceAsStream("hello_world.json")) {
            if (jsonStream == null) {
                throw new RuntimeException("failed to get hello_world.json");
            }

            jsonObject = gson.fromJson(new InputStreamReader(jsonStream), JsonObject.class);
        }

        var byteCodeHex = jsonObject.getAsJsonPrimitive("object").getAsString();
        var byteCode = ExampleHelper.parseHex(byteCodeHex);

        var operatorKey = ExampleHelper.getOperatorKey();
        var client = ExampleHelper.createHederaClient();

        // create the contract's bytecode file
        var fileTx = new FileCreateTransaction(client).setExpirationTime(
            Instant.now()
                .plus(Duration.ofSeconds(2592000))
        )
            // Use the same key as the operator to "own" this file
            .addKey(operatorKey.getPublicKey())
            .setContents(byteCode);

        var fileReceipt = fileTx.executeForReceipt();
        var newFileId = Objects.requireNonNull(fileReceipt.getFileId());

        System.out.println("contract bytecode file: " + newFileId);

        var contractTx = new ContractCreateTransaction(client)
            .setBytecodeFile(newFileId)
            .setConstructorParams("hello from hedera!".getBytes())
            // own the contract so we can destroy it later
            .setAdminKey(operatorKey.getPublicKey());

        var contractReceipt = contractTx.executeForReceipt();
        var newContractId = Objects.requireNonNull(contractReceipt.getContractId());

        System.out.println("new contract ID: " + newContractId);

        var contractCallResult = new ContractCallQuery(client)
            .setContract(newContractId)
            .execute();

        if (contractCallResult.getErrorMessage() != null) {
            System.out.println("error calling contract: " + contractCallResult.getErrorMessage());
            return;
        }

        var callResult = new String(contractCallResult.getCallResult());

        System.out.println("contract returned message: " + callResult);

        var contractUpdateResult = new ContractExecuteTransaction(client)
            .setContract(newContractId)
            .setFunctionParameters("hello from hedera again!".getBytes())
    }
}
