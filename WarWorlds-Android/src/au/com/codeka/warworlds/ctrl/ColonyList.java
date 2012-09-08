package au.com.codeka.warworlds.ctrl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.Html;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import au.com.codeka.RomanNumeralFormatter;
import au.com.codeka.warworlds.R;
import au.com.codeka.warworlds.model.Colony;
import au.com.codeka.warworlds.model.ImageManager;
import au.com.codeka.warworlds.model.Planet;
import au.com.codeka.warworlds.model.PlanetImageManager;
import au.com.codeka.warworlds.model.Sprite;
import au.com.codeka.warworlds.model.Star;
import au.com.codeka.warworlds.model.StarImageManager;

public class ColonyList extends FrameLayout {
    private Context mContext;
    private List<Colony> mColonies;
    private Map<String, Star> mStars;
    private Colony mSelectedColony;
    private boolean mIsInitialized;
    private ColonyListAdapter mColonyListAdapter;
    private ViewColonyHandler mViewColonyListener;

    public ColonyList(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;

        View child = inflate(context, R.layout.colony_list_ctrl, null);
        this.addView(child);
    }

    public void refresh(List<Colony> colonies, Map<String, Star> stars) {
        mColonies = colonies;
        mStars = stars;

        initialize();

        // if we had a colony selected, make sure we still have the same
        // colony selected after we refresh
        if (mSelectedColony != null) {
            Colony selectedColony = mSelectedColony;
            mSelectedColony = null;

            for (Colony c : mColonies) {
                if (c.getKey().equals(selectedColony.getKey())) {
                    mSelectedColony = c;
                    break;
                }
            }
        }

        mColonyListAdapter.setColonies(stars, colonies);
    }

    public void setOnViewColonyListener(ViewColonyHandler listener) {
        mViewColonyListener = listener;
    }

    private void initialize() {
        if (mIsInitialized) {
            return;
        }
        mIsInitialized = true;

        mColonyListAdapter = new ColonyListAdapter();
        final ListView colonyList = (ListView) findViewById(R.id.colonies);
        colonyList.setAdapter(mColonyListAdapter);

        colonyList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                mSelectedColony = mColonyListAdapter.getColonyAtPosition(position);
                mColonyListAdapter.notifyDataSetChanged();
                refreshStatistics();
            }
        });

        final Button viewBtn = (Button) findViewById(R.id.view_btn);
        viewBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectedColony != null && mViewColonyListener != null) {
                    Star star = mStars.get(mSelectedColony.getStarKey());
                    mViewColonyListener.onViewColony(star, mSelectedColony);
                }
            }
        });
    }

    private void refreshStatistics() {
        final TextView colonyInfo = (TextView) findViewById(R.id.colony_info);

        if (mSelectedColony == null) {
            colonyInfo.setText("");
        } else {
            String fmt = mContext.getString(R.string.colony_overview_format);
            String html = String.format(fmt,
                    (int) mSelectedColony.getPopulation(),
                    mSelectedColony.getFarmingFocus(),
                    mSelectedColony.getMiningFocus(),
                    mSelectedColony.getConstructionFocus()
                );
            colonyInfo.setText(Html.fromHtml(html));
        }
    }

    /**
     * This adapter is used to populate the list of colonies that we're looking at.
     */
    private class ColonyListAdapter extends BaseAdapter {
        private ArrayList<Colony> mColonies;
        private Map<String, Star> mStars;
        private Map<String, Bitmap> mBitmaps;

        public ColonyListAdapter() {
            // whenever a new star/planet bitmap is generated, redraw the list
            StarImageManager.getInstance().addBitmapGeneratedListener(
                    new ImageManager.BitmapGeneratedListener() {
                @Override
                public void onBitmapGenerated(String key, Bitmap bmp) {
                    notifyDataSetChanged();
                }
            });
            PlanetImageManager.getInstance().addBitmapGeneratedListener(
                    new ImageManager.BitmapGeneratedListener() {
                @Override
                public void onBitmapGenerated(String key, Bitmap bmp) {
                    notifyDataSetChanged();
                }
            });

            mBitmaps = new HashMap<String, Bitmap>();
        }

        /**
         * Sets the list of fleets that we'll be displaying.
         */
        public void setColonies(Map<String, Star> stars, List<Colony> colonies) {
            mColonies = new ArrayList<Colony>(colonies);
            mStars = stars;

            Collections.sort(mColonies, new Comparator<Colony>() {
                @Override
                public int compare(Colony lhs, Colony rhs) {
                    // sort by star, then by planet index
                    if (!lhs.getStarKey().equals(rhs.getStarKey())) {
                        Star lhsStar = mStars.get(lhs.getStarKey());
                        Star rhsStar = mStars.get(rhs.getStarKey());
                        return lhsStar.getName().compareTo(rhsStar.getName());
                    } else {
                        return lhs.getPlanetIndex() - rhs.getPlanetIndex();
                    }
                }
            });

            notifyDataSetChanged();
        }

        public Colony getColonyAtPosition(int position) {
            if (mColonies == null)
                return null;
            return mColonies.get(position);
        }

        @Override
        public int getCount() {
            if (mColonies == null)
                return 0;
            return mColonies.size();
        }

        @Override
        public Object getItem(int position) {
            return getColonyAtPosition(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService
                        (Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.colony_list_row, null);
            }

            Colony colony = mColonies.get(position);
            Star star = mStars.get(colony.getStarKey());
            Planet planet = star.getPlanets()[colony.getPlanetIndex() - 1];

            ImageView starIcon = (ImageView) view.findViewById(R.id.star_icon);
            ImageView planetIcon = (ImageView) view.findViewById(R.id.planet_icon);
            TextView colonyName = (TextView) view.findViewById(R.id.colony_name);
            TextView colonySummary = (TextView) view.findViewById(R.id.colony_summary);

            Bitmap bmp = mBitmaps.get(star.getKey());
            if (bmp == null) {
                int imageSize = (int)(star.getSize() * star.getStarType().getImageScale() * 2);
                bmp = StarImageManager.getInstance().getBitmap(mContext, star, imageSize);
                if (bmp != null) {
                    mBitmaps.put(star.getKey(), bmp);
                }
            }
            starIcon.setImageBitmap(bmp);

            Sprite sprite = PlanetImageManager.getInstance().getSprite(mContext, planet);
            planetIcon.setImageDrawable(sprite);

            colonyName.setText(String.format("%s %s", star.getName(), RomanNumeralFormatter.format(planet.getIndex())));
            colonySummary.setText(String.format("Pop: %d", (int) colony.getPopulation()));

            if (mSelectedColony != null && mSelectedColony.getKey().equals(colony.getKey())) {
                view.setBackgroundColor(0xff0c6476);
            } else {
                view.setBackgroundColor(0xff000000);
            }

            return view;
        }
    }

    public interface ViewColonyHandler {
        void onViewColony(Star star, Colony colony);
    }
}
