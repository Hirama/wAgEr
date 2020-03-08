package com.example.wager;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.os.StrictMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private final String privateKey = "";
    private final String wagerContractAddress = "0x7F2991f700832B065B8cEE9F3226957f8c04e595";
    private final OkHttpClient client = new OkHttpClient();
    Web3j web3 = Web3j.build(new HttpService("https://ropsten.infura.io/v3/18bdd762e4b84f1c85479b76ae563634"));
    //    Credentials credentials = Credentials.create(privateKey);
    Credentials credentials;
    //    WagerContract wagerContract = WagerContract.load(wagerContractAddress, web3, credentials, BigInteger.valueOf(1_000_000), BigInteger.valueOf(1_000_000));
    WagerContract wagerContract;

    File walletPath;
    String fileName = "keystore.json";

    TextView accountAddress;
    TextView accountBalance;
    TextView riskLevelText;
    TextView amountToLendText;
    TextView mlResult;
    Button createLoan;
    SeekBar riskBar;
    SeekBar amountBar;
    int amountToLendLevel;
    int riskLevel;
    BigInteger mainAccountBalance;
    BigInteger amountToLendInEth;
    ProgressDialog progressDialog;

    Map<String, Integer> wagerResultTable = new HashMap<>();
    TableLayout tableView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupBouncyCastle(); // set crypto provider

        // allow strict mode
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        setContentView(R.layout.activity_main);

        // Test views
        accountAddress = findViewById(R.id.textViewAccountAddress);
        accountBalance = findViewById(R.id.textViewAccountBalance);
        riskLevelText = findViewById(R.id.textViewRiskLevel);
        amountToLendText = findViewById(R.id.textViewLoanAmount);
        mlResult = findViewById(R.id.textViewMLResult);

        // table
        tableView = findViewById(R.id.assetsTable);
        tableView.setStretchAllColumns(true);

        // buttons
        createLoan = findViewById(R.id.buttonCreateWager);

        // Bars
        riskBar = findViewById(R.id.riskBar);
        riskLevel = riskBar.getProgress();
        amountBar = findViewById(R.id.amountBar);

        amountToLendLevel = amountBar.getProgress() * 10;

        riskLevelText.setText("Risk Level: " + riskBar.getProgress());
        amountToLendText.setText("Amount to lend: " + amountBar.getProgress() * 10 + " %");

        riskBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                riskLevelText.setText("Risk Level: " + progress);
                riskLevel = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Get ML Data
                try {
                    getML(riskLevel, amountToLendInEth);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        amountBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                amountToLendText.setText("Amount to lend: " + progress * 10 + " %");
                amountToLendLevel = progress * 10;

                amountToLendInEth = mainAccountBalance.divide(BigInteger.valueOf(100)).multiply(BigInteger.valueOf(amountToLendLevel));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Get ML Data
                try {
                    getML(riskLevel, amountToLendInEth);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            walletPath = this.getFilesDir();
            File f = new File(walletPath + "/" + fileName);
            if (f.exists() && !f.isDirectory()) {
                credentials = WalletUtils.loadCredentials("password", walletPath + "/" + fileName);
            } else {
                String generatedName = WalletUtils.generateLightNewWalletFile("password", walletPath);
                File generatedFile = new File(walletPath, generatedName);
                File keystore = new File(walletPath, fileName);
                generatedFile.renameTo(keystore);
                credentials = WalletUtils.loadCredentials("password", walletPath + "/" + fileName);
            }
            accountAddress.setText(credentials.getAddress());
            EthGetBalance balanceWei = web3.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();
            BigDecimal balanceInEther = Convert.fromWei(balanceWei.getBalance().toString(), Convert.Unit.ETHER);
            mainAccountBalance = balanceWei.getBalance();
            if (mainAccountBalance.compareTo(BigInteger.ZERO) > 0) {
                accountBalance.setText("ETH balance: " + balanceInEther.toPlainString());
            } else {
                accountBalance.setText("Please top up your balance");
            }
            amountToLendInEth = mainAccountBalance.divide(BigInteger.valueOf(100)).multiply(BigInteger.valueOf(amountToLendLevel));
            getML(riskLevel, amountToLendInEth);

        } catch (Exception e) {
            e.printStackTrace();
        }


        accountAddress.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("address", accountAddress.getText());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getApplication(), "Address copied!", Toast.LENGTH_SHORT).show();
        });

        createLoan.setOnClickListener(v -> {

            progressDialog = new ProgressDialog(MainActivity.this);
            progressDialog.setTitle("Please wait");
            progressDialog.setMessage("Waiting for transaction status...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            new Background().execute();
        });

    }

    private void setupBouncyCastle() {
        final Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (provider == null) {
            // Web3j will set up the provider lazily when it's first used.
            return;
        }
        if (provider.getClass().equals(BouncyCastleProvider.class)) {
            // BC with same package name, shouldn't happen in real life.
            return;
        }
        // Android registers its own BC provider. As it might be outdated and might not include
        // all needed ciphers, we substitute it with a known BC bundled in the app.
        // Android's BC has its package rewritten to "com.android.org.bouncycastle" and because
        // of that it's possible to have another BC implementation loaded in VM.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    void sendTx() {
        new Thread(() -> {
            TransactionReceipt receipt = null;
            try {
                wagerContract = WagerContract.load(wagerContractAddress, web3, credentials, BigInteger.valueOf(1_000_000), BigInteger.valueOf(1_000_000));
                receipt = wagerContract.createWager(credentials.getAddress(), amountToLendInEth, amountToLendInEth).send();
            } catch (Exception e) {
                e.printStackTrace();
            }
            progressDialog.cancel();
            assert receipt != null;
            Looper.prepare();
            Toast.makeText(MainActivity.this, "Tx Receipt complete: " + receipt.getTransactionHash(), Toast.LENGTH_LONG).show();
            updateBalance();
            Looper.loop();
        }).start();
    }

    void updateBalance() {
        EthGetBalance balanceWei = null;
        try {
            balanceWei = web3.ethGetBalance(credentials.getAddress(), DefaultBlockParameterName.LATEST).sendAsync().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        BigDecimal balanceInEther = Convert.fromWei(balanceWei.getBalance().toString(), Convert.Unit.ETHER);
        if (mainAccountBalance.intValue() > 0) {
            System.out.println(mainAccountBalance.toString());
            accountBalance.setText("ETH balance: " + balanceInEther.toPlainString());
        } else {
            accountBalance.setText("Please top up your balance");
        }
        System.out.println("Updated balance " + balanceInEther.toPlainString());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.bashboard) {
            Intent intent = new Intent(this, Dashboard.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void getML(int risk, BigInteger wager) throws Exception {
        Request request = new Request.Builder()
                .url("http://3.20.206.173/wager_function?risk=" + risk + "&wager=" + wager.toString())
                .build();
        System.out.println(request.url().toString());

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            Gson gson = new Gson();
            Type assetMapType = new TypeToken<Map<String, Integer>>() {
            }.getType();
            Map<String, Integer> stringIntegerMap = gson.fromJson(response.body().charStream(), assetMapType);

            for (Map.Entry<String, Integer> entry : stringIntegerMap.entrySet()) {
                if (!entry.getKey().equals("index")) {
                    wagerResultTable.put(entry.getKey(), entry.getValue());
                }
            }
        }


        int count = tableView.getChildCount();
        for (int i = 1; i < count; i++) {
            View child = tableView.getChildAt(i);
            if (child instanceof TableRow) ((ViewGroup) child).removeAllViews();
        }
        for (Map.Entry<String, Integer> entry : wagerResultTable.entrySet()) {
            System.out.println(entry.getKey() + "/" + entry.getValue());
            TableRow tr = new TableRow(this);
            TextView c1 = new TextView(this);
            c1.setText(entry.getKey());
            TextView c2 = new TextView(this);
            c2.setText(entry.getValue() + " %");
            tr.addView(c1);
            tr.addView(c2);
            tableView.addView(tr);
        }
    }

    class Background extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            sendTx();
            return null;
        }
    }
}


