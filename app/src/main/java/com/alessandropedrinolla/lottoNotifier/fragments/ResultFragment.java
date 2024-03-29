package com.alessandropedrinolla.lottoNotifier.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.alessandropedrinolla.lottoNotifier.interfaces.ResultFragmentInterface;
import com.google.gson.Gson;
import com.alessandropedrinolla.lottoNotifier.R;
import com.alessandropedrinolla.lottoNotifier.adapters.GamesAdapter;
import com.alessandropedrinolla.lottoNotifier.interfaces.GameAdapterListenerInterface;
import com.alessandropedrinolla.lottoNotifier.interfaces.ScraperListenerInterface;
import com.alessandropedrinolla.lottoNotifier.models.Game;
import com.alessandropedrinolla.lottoNotifier.models.ScrapeData;
import com.alessandropedrinolla.lottoNotifier.network.Scraper;
import com.alessandropedrinolla.lottoNotifier.utils.IOUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;

public class ResultFragment extends Fragment implements ScraperListenerInterface, GameAdapterListenerInterface, ResultFragmentInterface {
    private ListView mListView;
    private GamesAdapter mGamesAdapter;
    private ArrayList<Game> mGames;
    private ProgressBar mProgressBar;
    private int mProgress;
    private int mProgressMax;
    private IOUtil mIoUtil;
    private Gson mGson;
    private Context mContext;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mIoUtil = new IOUtil(context);
        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGames = new ArrayList<>();
        mGson = new Gson();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflatedView = inflater.inflate(R.layout.fragment_result, container, false);

        inflatedView.findViewById(R.id.check_button).setOnClickListener(view -> checkList());

        mProgressBar = inflatedView.findViewById(R.id.check_progress_bar);
        mListView = inflatedView.findViewById(R.id.list_view);

        setupListView();

        refreshList();

        return inflatedView;
    }

    public void refreshList() {
        mIoUtil.loadGames(mGames);
        mGamesAdapter.notifyDataSetChanged();
    }

    private void setupListView() {
        mGamesAdapter = new GamesAdapter(getContext(), mGames);
        mListView.setAdapter(mGamesAdapter);
        mGamesAdapter.gali = this;
    }

    private void checkList() {
        refreshList();

        Hashtable<String, HashSet<Integer>> dateGameIdSet = new Hashtable<>();
        Scraper.sli = this;

        for (Game g : mGames) {
            if (g.getNumbersHit() == -1) {
                if (dateGameIdSet.containsKey(g.getDate())) {
                    dateGameIdSet.get(g.getDate()).add(g.getId());
                } else {
                    HashSet<Integer> n = new HashSet<>();
                    n.add(g.getId());
                    dateGameIdSet.put(g.getDate(), n);
                }
            }
        }

        if (dateGameIdSet.keySet().size() > 0) {
            mProgress = 0;
            mProgressMax = dateGameIdSet.size();
            mProgressBar.setProgress(0);
            mProgressBar.setVisibility(View.VISIBLE);
            mProgressBar.setMax(mProgressMax);

            for (String date : dateGameIdSet.keySet()) {
                Scraper.getData(date, dateGameIdSet.get(date));
            }
        }
    }

    @Override
    public void onCompleted(String resultJSON) {
        ScrapeData scrapeData = mGson.fromJson(resultJSON, ScrapeData.class);
        mIoUtil.persistScrapeData(scrapeData);

        String date = scrapeData.getDate();
        Handler handler = new Handler(mContext.getMainLooper());

        // edit the mGames that have the resultJSON date that are not yet set
        for (Game g : mGames) {
            if (g.getNumbersHit() == -1 && g.getDate().equals(date)) {
                String numbers = scrapeData.getExtractions().get(g.getId()).getNumbersAsString();
                Runnable checkRunnable = () -> g.checkNumbersHit(numbers);
                handler.post(checkRunnable);
                mIoUtil.persistGame(g);
            }
        }

        Runnable runnable = () -> mGamesAdapter.notifyDataSetChanged();
        handler.post(runnable);

        // Progress bar advance
        mProgress++;
        mProgressBar.setProgress(mProgress);

        if (mProgress == mProgressMax) {
            Runnable updateDataRunnable = () -> {
                mProgressBar.setVisibility(View.GONE);
                mIoUtil.loadGames(mGames);
                mGamesAdapter.notifyDataSetChanged();
            };
            handler.post(updateDataRunnable);
        }
    }

    @Override
    public void deleteGame(Game game) {
        mIoUtil.deleteGame(game.getUUID());
        refreshList();
    }
}