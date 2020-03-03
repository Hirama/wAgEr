package com.example.wager;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.anychart.AnyChart;
import com.anychart.AnyChartView;
import com.anychart.chart.common.dataentry.DataEntry;
import com.anychart.chart.common.dataentry.ValueDataEntry;
import com.anychart.charts.Pie;

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class Dashboard extends AppCompatActivity {

    private final String wagerContractAddress = "0x7F2991f700832B065B8cEE9F3226957f8c04e595";
    private final String privateKey = "";
    Web3j web3 = Web3j.build(new HttpService("https://ropsten.infura.io/v3/18bdd762e4b84f1c85479b76ae563634"));
    Credentials credentials = Credentials.create(privateKey);

    Wager wagerContract = Wager.load(wagerContractAddress, web3, credentials, BigInteger.valueOf(1_000_000), BigInteger.valueOf(1_000_000));

    TextView networkCapacityText;
    TextView votingPowerText;
    TextView estimatedIRText;
    ProgressDialog progressDialog;
    Button withdrawAll;

    BigInteger networkCapacity = BigInteger.valueOf(0);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        networkCapacityText = findViewById(R.id.textViewNetwokCapacity);
        votingPowerText = findViewById(R.id.textViewMyVotingPower);
        estimatedIRText = findViewById(R.id.textViewEstimatedIR);

        estimatedIRText.append("12 %");

        withdrawAll = findViewById(R.id.buttonWithdrawAll);


        updateData();

        withdrawAll.setOnClickListener(v -> {
            progressDialog = new ProgressDialog(Dashboard.this);
            progressDialog.setTitle("Processing...");
            progressDialog.setMessage("Waiting for transaction to be mined...");
            progressDialog.setCancelable(false);
            progressDialog.show();

            new Background().execute();
        });

        Pie pie = AnyChart.pie();

        List<DataEntry> data = new ArrayList<>();
        data.add(new ValueDataEntry("BTC", 30));
        data.add(new ValueDataEntry("DAI", 20));
        data.add(new ValueDataEntry("ETH", 30));
        data.add(new ValueDataEntry("XAU", 20));

        pie.data(data);

        AnyChartView anyChartView = findViewById(R.id.any_chart_view);
        anyChartView.setChart(pie);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
                                      @Override
                                      public void run() {
                                          updateData();
                                      }
                                  },
                0, 5000);
    }


    void withdraw() {
        new Thread(() -> {
            TransactionReceipt receipt = null;
            try {
                receipt = wagerContract.withdrawAll().sendAsync().get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            progressDialog.cancel();
            assert receipt != null;
            Looper.prepare();
            Toast.makeText(Dashboard.this, "Tx Receipt complete: " + receipt.getTransactionHash(), Toast.LENGTH_LONG).show();
            Looper.loop();
        }).start();
    }

    void updateData() {
        try {
            System.out.println("Load stat..");
            networkCapacityText.setText("Network Capacity: " + wagerContract.totalSupply().send() + " WGRI");
            votingPowerText.setText("My Voting Power: " + wagerContract.myVotingPower(credentials.getAddress()).send().toString() + " WGRI");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class Background extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            withdraw();
            updateData();
            return null;
        }
    }
}
