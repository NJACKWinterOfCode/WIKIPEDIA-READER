package com.riteshmohapatra.wikipediareader;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RequestQueue queue;
    private TextToSpeech tts;

    // viewer
    private TextView textView;
    private ImageView imageView;
    private ProgressBar progress;
    private View textViewer;

    // results
    private ListView resultsList;
    private List<String> results = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private FloatingActionButton fab;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.queue = Volley.newRequestQueue(MainActivity.this);

        // Initialize TextToSpeech engine.
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                // noop
            }
        });
        tts.setLanguage(Locale.getDefault());

        setContentView(R.layout.activity_main);

        // Initialize the views
        textView = findViewById(R.id.textView);
        imageView = findViewById(R.id.image);
        progress = findViewById(R.id.progressBar);
        textViewer = findViewById(R.id.textViewer);
        resultsList = findViewById(R.id.results);
        fab = findViewById(R.id.volume);
        emptyView = findViewById(R.id.empty_view);
        emptyView.setVisibility(View.VISIBLE);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar); // set toolbar as the ActionBar

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, results);
        resultsList.setAdapter(adapter);
        resultsList.setItemsCanFocus(false);
        resultsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                display(results.get(position));
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isTextVisible) {
                    tts.speak("Please search something first.", TextToSpeech.QUEUE_FLUSH, null);
                } else {
                    if (tts.isSpeaking()) {
                        tts.stop();
                        fab.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_play_arrow));
                    } else {
                        fab.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_stop));
                        // flush the tts queue
                        tts.speak(" ", TextToSpeech.QUEUE_FLUSH, null);

                        // Divide string into chunks
                        String article = textView.getText().toString();
                        for (int index = 0; index < article.length(); index += 3000)
                            tts.speak(article.substring(index, Math.min(index + 3000, article.length())),
                                    TextToSpeech.QUEUE_ADD, null); // add chunk to queue
                    }
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.tts.stop();
        //this.queue.stop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        this.queue.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.tts.shutdown();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu,menu);

        final MenuItem searchMenuItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView)searchMenuItem.getActionView();

        searchView.setQueryHint("Search Wikipedia");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override       // what happens when the user submits a query
            public boolean onQueryTextSubmit(String query) {
                searchMenuItem.collapseActionView(); // collapse the search box
                if (tts.isSpeaking()) {     // if tts is speaking, stop it
                    tts.stop();
                    fab.setImageDrawable(ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_play_arrow));
                }
                search(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String query) {
                // do nothing
                return false;
            }
        });

        searchView.setOnQueryTextFocusChangeListener(new OnFocusChangeListener() {
            @Override       // hide the search box if the user focuses on something else
            public void onFocusChange(View view, boolean queryTextFocused) {
                if(!queryTextFocused) {
                    searchView.setIconified(true);
                    searchMenuItem.collapseActionView();
                }
            }
        });

        return true;
    }

    @Override
    public void onBackPressed() {
        if (results.size() > 0 && resultsList.getVisibility() != View.VISIBLE) {
            setTitle("Search Results");
            isTextVisible=false;
            textViewer.setVisibility(View.INVISIBLE);
            resultsList.setVisibility(View.VISIBLE);
        } else {
            super.onBackPressed();
        }
    }

    private void search(String query) {
        isTextVisible=false;
        String url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srprop=&format=json&srsearch=" + encodeURIComponent(query);

        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {       // response received
                        progress.setVisibility(View.INVISIBLE);
                        try {
                            JSONArray jsonresults = response.getJSONObject("query").getJSONArray("search");
                            for (int i=0; i<jsonresults.length(); i++)
                                results.add(jsonresults.getJSONObject(i).getString("title"));
                            adapter.notifyDataSetChanged();
                        } catch (JSONException ex) {                        // response could not be parsed.
                            Toast.makeText(MainActivity.this,"Error in parsing response", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {        // no response
                        //progress.setVisibility(View.INVISIBLE);
                        // todo: view image
                        Toast.makeText(MainActivity.this,"Error in getting response", Toast.LENGTH_SHORT).show();

                    }
                });


        // Add the request to the RequestQueue.
        emptyView.setVisibility(View.INVISIBLE);
        textViewer.setVisibility(View.INVISIBLE);
        progress.setVisibility(View.VISIBLE);           // make the progress bar visible
        results.clear(); adapter.notifyDataSetChanged();
        resultsList.setVisibility(View.VISIBLE);
        setTitle("Search Results");
        MainActivity.this.queue.add(jsObjRequest);
    }

    private boolean isTextVisible= false;

    private void display(String title) {        // fetches the article and loads it into the viewer.
        textViewer.requestFocus();
        String url = "https://en.wikipedia.org/w/api.php?action=query&prop=pageimages|extracts&format=json&piprop=thumbnail&pithumbsize=300&titles=" + encodeURIComponent(title);

        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {       // response received
                        progress.setVisibility(View.INVISIBLE);         // hide the progress bar
                        imageView.setVisibility(View.GONE);
                        try {
                            JSONObject pages = response.getJSONObject("query").getJSONObject("pages");
                            String firstPage = pages.keys().next();         // extract the first result
                            String text = pages.getJSONObject(firstPage).getString("extract");
                            try {       // not all articles have images
                                String imgurl = pages.getJSONObject(firstPage).getJSONObject("thumbnail").getString("source");

                                imageView.setVisibility(View.VISIBLE);
                                Picasso.with(getApplicationContext()).load(imgurl).placeholder(R.drawable.placeholder).resize(600,0).into(imageView);
                            } catch (JSONException e) { } // do nothing
                            isTextVisible=true;
                            emptyView.setVisibility(View.GONE);
                            textViewer.setVisibility(View.VISIBLE);         // make viewer visible
                            textView.setText(Html.fromHtml(text));          // load the content into the viewer.
                        } catch (JSONException ex) {                        // response could not be parsed.
                            Toast.makeText(MainActivity.this,"Error in parsing response", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {        // no response
                        progress.setVisibility(View.INVISIBLE);
                        // todo: view image
                        Toast.makeText(MainActivity.this,"Error in getting response", Toast.LENGTH_SHORT).show();

                    }
                });


        // Add the request to the RequestQueue.
        setTitle(title);
        textViewer.setVisibility(View.INVISIBLE);
        progress.setVisibility(View.VISIBLE);           // make the progress bar visible
        resultsList.setVisibility(View.INVISIBLE);
        MainActivity.this.queue.add(jsObjRequest);
    }

    private static String encodeURIComponent(String s) {
        String result;

        try
        {
            result = URLEncoder.encode(s, "UTF-8")
                    .replaceAll("\\+", "%20")
                    .replaceAll("%21", "!")
                    .replaceAll("%27", "'")
                    .replaceAll("%28", "(")
                    .replaceAll("%29", ")")
                    .replaceAll("%7E", "~");
        }

        // This exception should never occur.
        catch (UnsupportedEncodingException e)
        {
            result = s;
        }

        return result;
    }
}