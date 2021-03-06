package com.speedyao.spider.lianjia;

import cn.wanghaomiao.xpath.model.JXDocument;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.speedyao.spider.JsoupWrapper;
import com.speedyao.spider.lianjia.vo.HouseVo;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.seimicrawler.xpath.exception.XpathSyntaxErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by speedyao on 2018/10/12.
 */
public class LianjiaSpider {
    static Logger logger = LoggerFactory.getLogger(LianjiaSpider.class);
    final static int TIME_OUT = 30 * 1000;

    /**
     * 获取链家的房产信息
     *
     * @param content
     * @throws IOException
     */
    public static List<HouseVo> getLianjiaByContent(String content) throws IOException {
        List<HouseVo> list = new ArrayList<>();
        String baseUrl = "https://tj.lianjia.com";
        String url = baseUrl + "/ershoufang/rs" + URLEncoder.encode(content, "UTF-8") + "/";
        logger.info(content + ">>开始请求：" + url);
        Document document = JsoupWrapper.parse(new URL(url), TIME_OUT);
        logger.info(content + ">>请求成功：" + url);
        parseSellList(list, document);
        //判断是否有分页数据
        Elements pages = document.getElementsByAttributeValue("comp-module", "page");
        if (pages.size() <= 0) {
            return list;
        }
        String pageUrlBase = pages.get(0).attr("page-url");
        JSONObject pageData = JSON.parseObject(pages.get(0).attr("page-data"));
        Integer totalPage = pageData.getInteger("totalPage");
        logger.info(content + ">>有分页数据，共有" + totalPage + "页");
        if (totalPage > 1) {
            for (int i = 2; i <= totalPage; i++) {
                String pageUrl = baseUrl + pageUrlBase.replaceFirst("\\{page}", String.valueOf(i));
                Document pageDocument;
                try {
                    logger.info("{}>>开始请求：{}", content, url);
                    pageDocument = JsoupWrapper.parse(new URL(pageUrl), 30 * 1000);
                    logger.info(content + ">>请求第{}页数据成功", i);
                    parseSellList(list, pageDocument);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return list;
    }

    private static void parseSellList(List<HouseVo> list, Document document) {
        Elements sellList = document.getElementsByAttributeValueContaining("class", "sellListContent");
        if (sellList.size() > 0) {
            Elements children = sellList.get(0).children();
            logger.info("共有" + children.size() + "条数据");
            children.forEach(element -> {
                try {
                    HouseVo vo = new HouseVo();
                    Element info = element.getElementsByClass("info clear").get(0);
                    Element title = info.getElementsByClass("title").get(0);
                    vo.setUrl(title.getElementsByTag("a").get(0).attr("href"));
                    vo.setTitle(title.text());
                    //小区、概况
                    Element houseInfo = info.getElementsByClass("houseInfo").get(0);
                    Elements xiaoqu = houseInfo.getElementsByTag("a");
                    vo.setXiaoqu(xiaoqu.text());
                    vo.setXiaoquUrl(xiaoqu.attr("href"));
                    vo.setInfo(houseInfo.text());
                    //价格
                    Element priceInfo = element.getElementsByClass("priceInfo").get(0);
                    String totalPrice = priceInfo.getElementsByClass("totalPrice").get(0).getElementsByTag("span").text();
                    vo.setTotalPrice(Double.parseDouble(totalPrice));
                    String unitPrice = priceInfo.getElementsByClass("unitPrice").get(0).getElementsByTag("span").text();
                    vo.setUnitPrice(unitPrice);
                    //positionInfo
                    Element positionInfo = element.getElementsByClass("positionInfo").get(0);
                    vo.setFlood(positionInfo.text());
                    vo.setPositionUrl(positionInfo.getElementsByTag("a").attr("href"));
                    vo.setPosition(positionInfo.getElementsByTag("a").text());
                    //followInfo
                    vo.setFollowInfo(element.getElementsByClass("followInfo").text());
                    //tag
                    Elements tag = element.getElementsByClass("tag");
                    StringBuilder tagSb = new StringBuilder();
                    tag.get(0).children().forEach(span -> tagSb.append(span.text()).append("-"));
                    vo.setTag(tagSb.toString());
                    element.getElementsByClass("positionInfo");
                    list.add(vo);
                } catch (Exception e) {
                    logger.error(element.toString());
                    logger.error(e.getMessage(), e);
                }
            });

        }
    }

    public static final String XPATH_BASE = "//*[@id=\"introduction\"]/div/div/div[1]/div[2]/ul";//基本信息
    public static final String XPATH_DEAL = "//*[@id=\"introduction\"]/div/div/div[2]/div[2]/ul";//交易信息

    public static JSONObject getHouseDetail(String url) throws IOException, XpathSyntaxErrorException {

        JSONObject json = new JSONObject();
        Document document = JsoupWrapper.parse(new URL(url), TIME_OUT);
        JXDocument jxDocument = new JXDocument(document);
        List<Object> baseInfo = jxDocument.sel(XPATH_BASE);
        if (!baseInfo.isEmpty()) {
            Element element = (Element) baseInfo.get(0);
            Elements children = element.children();
            JSONObject baseJson = new JSONObject();
            for (Element child : children) {
                String key = child.getElementsByTag("span").text();
                String value = child.text().replace(key, "");
                baseJson.put(key, value);
            }
            json.put("baseInfo", baseJson);
        }
        List<Object> dealInfo = jxDocument.sel(XPATH_DEAL);
        if (!dealInfo.isEmpty()) {
            Element element = (Element) dealInfo.get(0);
            Elements children = element.children();
            JSONObject dealJson = new JSONObject();
            children.forEach(child -> {
                Elements span = child.getElementsByTag("span");
                dealJson.put(span.get(0).text(), span.get(1).text());
            });
            json.put("dealInfo", dealJson);
        }
        return json;
    }

}
