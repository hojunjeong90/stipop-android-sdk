package io.stipop.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AbsListView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.stipop.*
import io.stipop.activity.DetailActivity
import io.stipop.adapter.AllStickerAdapter
import io.stipop.adapter.PackageAdapter
import io.stipop.adapter.PopularStickerAdapter
import io.stipop.adapter.RecentKeywordAdapter
import io.stipop.databinding.FragmentAllStickerBinding
import io.stipop.extend.RecyclerDecoration
import io.stipop.extend.TagLayout
import io.stipop.model.SPPackage
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder


class AllStickerFragment : Fragment() {
    lateinit private var _binding: FragmentAllStickerBinding
    lateinit private var _context: Context

    var packagePage = 2 // 1 Page -> Trending List
    var totalPage = 2
    lateinit var packageAdapter: PackageAdapter
    var packageData = ArrayList<SPPackage>()

    lateinit var allStickerAdapter: AllStickerAdapter
    var allStickerData = ArrayList<SPPackage>()

    private var lastItemVisibleFlag = false

    lateinit var packageRV: RecyclerView
    lateinit var trendingLL: LinearLayout

    lateinit var recentKeywordAdapter: RecentKeywordAdapter
    var recentKeywords = ArrayList<String>()

    var popularStickers = ArrayList<SPPackage>()
    lateinit var popularStickerAdapter: PopularStickerAdapter

    lateinit var recommendedTagsTL: TagLayout
    lateinit var popularStickerRV: RecyclerView

    lateinit var noneTV: TextView

    var inputKeyword = ""


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAllStickerBinding.inflate(layoutInflater, container, false)
        return _binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _context = view.context

        val drawable = _binding.searchbarLL.background as GradientDrawable
        drawable.setColor(Color.parseColor(Config.themeGroupedContentBackgroundColor)) // solid  color
        drawable.cornerRadius = Utils.dpToPx(Config.searchbarRadius.toFloat())

        _binding.keywordET.setTextColor(Config.getSearchTitleTextColor(_context))

        _binding.searchIconIV.setImageResource(Config.getSearchbarResourceId(_context))
        _binding.eraseIV.setImageResource(Config.getEraseResourceId(_context))


        _binding.searchIconIV.setIconDefaultsColor()
        _binding.eraseIV.setIconDefaultsColor()


        val headerV = View.inflate(_context, R.layout.header_all_sticker, null)

        headerV.findViewById<View>(R.id.underLineV)
            .setBackgroundColor(Config.getUnderLineColor(_context))
        headerV.findViewById<TextView>(R.id.trendingTV)
            .setTextColor(Config.getTitleTextColor(_context))
        headerV.findViewById<TextView>(R.id.stickersTV)
            .setTextColor(Config.getTitleTextColor(_context))


        packageRV = headerV.findViewById(R.id.packageRV)
        trendingLL = headerV.findViewById(R.id.trendingLL)

        _binding.stickerLV.addHeaderView(headerV)

        _binding.clearTextLL.setOnClickListener {
            _binding.keywordET.setText("")
            inputKeyword = ""

            Utils.hideKeyboard(_context)

            reloadData(true)
        }

        _binding.keywordET.setOnClickListener {
            changeView(true)

//            getRecentKeyword()
        }

        _binding.keywordET.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                changeView(true)

//                getRecentKeyword()
            }
        }

        _binding.keywordET.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {

            }

            override fun afterTextChanged(p0: Editable?) {

            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                inputKeyword = Utils.getString(_binding.keywordET)
            }
        })

        _binding.keywordET.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                reloadData(inputKeyword.isEmpty())
            }
            false
        }


        packageAdapter = PackageAdapter(packageData, _context)

        val mLayoutManager = LinearLayoutManager(_context)
        mLayoutManager.orientation = LinearLayoutManager.HORIZONTAL

        packageRV.layoutManager = mLayoutManager
        packageRV.addItemDecoration(RecyclerDecoration(Utils.dpToPx(6F).toInt()))
        packageRV.adapter = packageAdapter

        packageAdapter.setOnItemClickListener(object : PackageAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                if (position > packageData.size) {
                    return
                }

                val packageObj = packageData[position]

                goDetail(packageObj.packageId)
            }
        })

        if (Config.storeListType == "singular") {
            // B Type
            allStickerAdapter =
                AllStickerAdapter(_context, R.layout.item_all_sticker_type_b, allStickerData, this)
        } else {
            // A Type
            allStickerAdapter =
                AllStickerAdapter(_context, R.layout.item_all_sticker_type_a, allStickerData, this)
        }

        _binding.stickerLV.adapter = allStickerAdapter
        _binding.stickerLV.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(absListView: AbsListView?, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && lastItemVisibleFlag && totalPage > packagePage) {
                    packagePage += 1
                    val keyword = Utils.getString(_binding.keywordET)
                    loadPackageData(packagePage, keyword.isNotEmpty())
                }
            }

            override fun onScroll(
                view: AbsListView?,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                lastItemVisibleFlag =
                    (totalItemCount > 0) && (firstVisibleItem + visibleItemCount >= totalItemCount);
            }

        })
        _binding.stickerLV.setOnItemClickListener { adapterView, view, i, l ->
            // position - 1 : addHeaderView 해줬기 때문!
            val position = i - 1
            if (position < 0 && position > allStickerData.size) {
                return@setOnItemClickListener
            }

            val packageObj = allStickerData[position]
            goDetail(packageObj.packageId)
        }

        allStickerAdapter.notifyDataSetChanged()


        val recentHeaderV = View.inflate(_context, R.layout.header_recent_keyword, null)
        recentHeaderV.findViewById<TextView>(R.id.keywordClearTV).setOnClickListener {
            deleteKeyword(null)
        }

//       binding.recentLV.addHeaderView(recentHeaderV)

        val recentFooterV = View.inflate(_context, R.layout.footer_recent_keyword, null)
        val popularStickerLL = recentFooterV.findViewById<LinearLayout>(R.id.popularStickerLL)
        val recommendedTagLL = recentFooterV.findViewById<LinearLayout>(R.id.recommendedTagLL)
        noneTV = recentFooterV.findViewById<TextView>(R.id.noneTV)

        recommendedTagsTL = recentFooterV.findViewById(R.id.recommendedTagsTL)
        popularStickerRV = recentFooterV.findViewById(R.id.popularStickerRV)

        popularStickerAdapter = PopularStickerAdapter(popularStickers, _context)

        val mLayoutManager2 = LinearLayoutManager(_context)
        mLayoutManager2.orientation = LinearLayoutManager.HORIZONTAL

        popularStickerRV.layoutManager = mLayoutManager2
        popularStickerRV.addItemDecoration(RecyclerDecoration(Utils.dpToPx(7F).toInt()))
        popularStickerRV.adapter = popularStickerAdapter

        popularStickerAdapter.setOnItemClickListener(object :
            PopularStickerAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                if (position > popularStickers.size) {
                    return
                }

                val packageObj = popularStickers[position]

                goDetail(packageObj.packageId)
            }
        })

        _binding.recentLV.addFooterView(recentFooterV)

        recentKeywordAdapter =
            RecentKeywordAdapter(_context, R.layout.item_recent_keyword, recentKeywords, this)
        _binding.recentLV.adapter = recentKeywordAdapter
        _binding.recentLV.setOnItemClickListener { adapterView, view, i, l ->
            // position - 1 : addHeaderView 해줬기 때문!
            val position = i - 1
            if (position < 0 && position > allStickerData.size) {
                return@setOnItemClickListener
            }
        }

        loadPackageData(1, false)

        loadPackageData(packagePage, false)

        if (Config.storeRecommendedTagShow) {
            recommendedTagLL.visibility = View.VISIBLE
            popularStickerLL.visibility = View.GONE

            getKeyword()
        } else {
            recommendedTagLL.visibility = View.GONE
            popularStickerLL.visibility = View.VISIBLE

            getPopularStickers()
        }

    }


    val startForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val intent = result.data // Handle the Intent //do stuff here
                if (null != intent) {
                    val packageId = intent.getIntExtra("packageId", -1)
                    if (packageId < 0) {
                        return@registerForActivityResult
                    }

                    for (i in 0 until allStickerData.size) {
                        val item = allStickerData[i]
                        if (item.packageId == packageId) {
                            item.download = "Y"
                            break
                        }
                    }

                    allStickerAdapter.notifyDataSetChanged()

                }
            }
        }

    fun goDetail(packageId: Int) {
        val intent = Intent(_context, DetailActivity::class.java)
        intent.putExtra("packageId", packageId)
        // startActivity(intent)
        startForResult.launch(intent)
    }

    fun reloadData(all: Boolean) {
        if (all) {
            loadPackageData(1, false)

            packagePage = 2
        } else {
            packagePage = 1
        }

        totalPage = packagePage
        loadPackageData(packagePage, !all)
    }

    fun loadPackageData(page: Int, search: Boolean) {

        val params = JSONObject()
        params.put("userId", Stipop.userId)
        params.put("pageNumber", page)
        params.put("lang", Stipop.lang)
        params.put("countryCode", Stipop.countryCode)
        params.put("limit", 12)
        params.put("q", Utils.getString(_binding.keywordET))

        APIClient.get(
            activity as Activity,
            APIClient.APIPath.PACKAGE.rawValue,
            params
        ) { response: JSONObject?, e: IOException? ->

            if (search) {
                trendingLL.visibility = View.GONE

                packageData.clear()
                packageAdapter.notifyDataSetChanged()

                if (page == 1) {
                    allStickerData.clear()
                    allStickerAdapter.notifyDataSetChanged()
                }
            } else {
                trendingLL.visibility = View.VISIBLE
                if (page == 1) {
                    packageData.clear()
                    packageAdapter.notifyDataSetChanged()
                } else if (page == 2) {
                    allStickerData.clear()
                    allStickerAdapter.notifyDataSetChanged()
                }
            }

            if (null != response) {

                if (!response.isNull("body")) {
                    val body = response.getJSONObject("body")

                    if (!body.isNull("pageMap")) {
                        val pageMap = body.getJSONObject("pageMap")
                        totalPage = Utils.getInt(pageMap, "pageCount")
                    }

                    if (!body.isNull("packageList")) {
                        val packageList = body.getJSONArray("packageList")

                        for (i in 0 until packageList.length()) {
                            val item = packageList.get(i) as JSONObject

                            val spPackage = SPPackage(item)
                            if (page == 1 && !search) {
                                packageData.add(spPackage)
                            } else {
                                allStickerData.add(spPackage)
                            }
                        }

                        if (page == 1 && !search) {
                            packageAdapter.notifyDataSetChanged()
                        } else {
                            allStickerAdapter.notifyDataSetChanged()
                        }

                        if (page == 1) {
                            _binding.stickerLV.smoothScrollToPosition(0)
                        }
                    }

                }

            }

            if (search) {
                if (page == 1) {
                    if (allStickerData.count() > 0) {
                        noneTV.visibility = View.GONE
                        changeView(false)
                        Utils.hideKeyboard(_context)
                    } else {
                        noneTV.visibility = View.VISIBLE
                    }
                }
            } else {
                trendingLL.visibility = View.VISIBLE
                if (page == 1) {
                    if (packageData.count() > 0) {
                        noneTV.visibility = View.GONE
                        changeView(false)
                        Utils.hideKeyboard(_context)
                    } else {
                        noneTV.visibility = View.VISIBLE
                    }
                } else if (page == 2) {
                    if (allStickerData.count() > 0) {
                        noneTV.visibility = View.GONE
                        changeView(false)
                        Utils.hideKeyboard(_context)
                    } else {
                        noneTV.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    fun getPackInfo(idx: Int, packageId: Int) {

        val params = JSONObject()
        params.put("userId", Stipop.userId)

        APIClient.get(
            activity as Activity,
            APIClient.APIPath.PACKAGE.rawValue + "/${packageId}",
            params
        ) { response: JSONObject?, e: IOException? ->
            // println(response)

            if (null != response) {

                val header = response.getJSONObject("header")

                if (!response.isNull("body") && Utils.getString(header, "status") == "success") {
                    val body = response.getJSONObject("body")
                    val packageObj = body.getJSONObject("package")

                    val spPackage = SPPackage(packageObj)

                    downloadPackage(idx, spPackage)
                }

            } else {
                e?.printStackTrace()
            }
        }

    }

    fun downloadPackage(idx: Int, spPackage: SPPackage) {

        val params = JSONObject()
        params.put("userId", Stipop.userId)
        params.put("isPurchase", Config.allowPremium)
        params.put("lang", Stipop.lang)
        params.put("countryCode", Stipop.countryCode)

        if (Config.allowPremium == "Y") {
            // 움직이지 않는 스티커
            var price = Config.pngPrice

            if (spPackage.packageAnimated == "Y") {
                // 움직이는 스티커
                price = Config.gifPrice
            }
            params.put("price", price)
        }

        APIClient.post(
            activity as Activity,
            APIClient.APIPath.DOWNLOAD.rawValue + "/${spPackage.packageId}",
            params
        ) { response: JSONObject?, e: IOException? ->

            if (null != response) {

                val header = response.getJSONObject("header")

                if (Utils.getString(header, "status") == "success") {

                    // download
                    PackUtils.downloadAndSaveLocal(activity as Activity, spPackage) {
                        allStickerAdapter.setDownload(idx)
                        Toast.makeText(_context, "다운로드 완료!", Toast.LENGTH_LONG).show()
                        allStickerAdapter.notifyDataSetChanged()
                    }
                }

            } else {
                e?.printStackTrace()
            }
        }
    }

    fun getRecentKeyword() {

        recentKeywords.clear()
        recentKeywordAdapter.notifyDataSetChanged()

        var params = JSONObject()
        params.put("userId", Stipop.userId)

        APIClient.get(
            activity as Activity,
            APIClient.APIPath.SEARCH_RECENT.rawValue,
            params
        ) { response: JSONObject?, e: IOException? ->
            // println(response)

            if (null != response) {

                if (!response.isNull("body")) {
                    val body = response.getJSONObject("body")

                    if (!body.isNull("keywordList")) {
                        val keywordList = body.getJSONArray("keywordList")

                        for (i in 0 until keywordList.length()) {
                            val item = keywordList.get(i) as JSONObject

                            recentKeywords.add(Utils.getString(item, "keyword"))
                        }

                        recentKeywordAdapter.notifyDataSetChanged()
                    }

                }

            }
        }

    }

    fun getKeyword() {
        recommendedTagsTL.removeAllViews()

        APIClient.get(
            activity as Activity,
            APIClient.APIPath.SEARCH_KEYWORD.rawValue,
            null
        ) { response: JSONObject?, e: IOException? ->

            if (null != response) {

                if (!response.isNull("body")) {
                    val body = response.getJSONObject("body")

                    if (!body.isNull("keywordList")) {
                        val keywordList = body.getJSONArray("keywordList")

                        var limit = keywordList.length()
                        if (limit > 10) {
                            limit = 10
                        }

                        for (i in 0 until limit) {
                            val item = keywordList.get(i) as JSONObject

                            val keyword = Utils.getString(item, "keyword")

                            val tagView = layoutInflater.inflate(R.layout.tag_layout, null, false)
                            val tagTV = tagView.findViewById<TextView>(R.id.tagTV)

                            val drawable = tagTV.background as GradientDrawable
                            drawable.setStroke(1, Color.parseColor(Config.themeMainColor))

                            tagTV.setTextColor(Color.parseColor(Config.themeMainColor))
                            tagTV.text = keyword
                            tagTV.setOnClickListener {

                                // haptics
                                val vibrator =
                                    this._context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(
                                        VibrationEffect.createOneShot(
                                            100,
                                            VibrationEffect.DEFAULT_AMPLITUDE
                                        )
                                    )
                                }

                                changeView(false)
                                inputKeyword = keyword
                                _binding.keywordET.setText(keyword)
                                reloadData(false)
                            }

                            recommendedTagsTL.addView(tagView)
                        }

                    }

                }

            }

        }
    }

    fun getPopularStickers() {

        popularStickers.clear()

        val params = JSONObject()
        params.put("userId", Stipop.userId)
        params.put("limit", 4)

        APIClient.get(
            activity as Activity,
            APIClient.APIPath.PACKAGE.rawValue,
            params
        ) { response: JSONObject?, e: IOException? ->

            if (null != response) {

                if (!response.isNull("body")) {
                    val body = response.getJSONObject("body")

                    if (!body.isNull("packageList")) {
                        val packageList = body.getJSONArray("packageList")

                        for (i in 0 until packageList.length()) {
                            val item = packageList.get(i) as JSONObject
                            popularStickers.add(SPPackage(item))
                        }

                        popularStickerAdapter.notifyDataSetChanged()

                    }

                }

            }

        }
    }

    fun deleteKeyword(keyword: String?) {

        var path = APIClient.APIPath.SEARCH_RECENT.rawValue + "/${Stipop.userId}"
        if (!keyword.isNullOrEmpty()) {
            val encodeKeyword = URLEncoder.encode(keyword, "UTF-8")
            path += "/$encodeKeyword"
        }

        APIClient.delete(
            activity as Activity,
            path,
            null
        ) { response: JSONObject?, e: IOException? ->

            // println(response)

            if (null != response) {

                if (!response.isNull("header")) {
                    val header = response.getJSONObject("header")

                    if (Utils.getString(header, "status") == "success") {
                        if (!keyword.isNullOrEmpty()) {
                            for (i in 0 until recentKeywords.size) {
                                if (recentKeywords[i] == keyword) {
                                    recentKeywords.removeAt(i)
                                    break
                                }
                            }
                        } else {
                            recentKeywords.clear()
                        }

                        recentKeywordAdapter.notifyDataSetChanged()
                    }

                }

            }

        }
    }

    fun changeView(search: Boolean) {
        if (search) {
            _binding.recentLV.visibility = View.VISIBLE
            _binding.stickerLV.visibility = View.GONE
        } else {
            _binding.recentLV.visibility = View.GONE
            _binding.stickerLV.visibility = View.VISIBLE
        }
    }
}