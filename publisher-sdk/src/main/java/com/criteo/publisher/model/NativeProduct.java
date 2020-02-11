package com.criteo.publisher.model;

import org.json.JSONException;
import org.json.JSONObject;

public class NativeProduct {

  private static final String TITLE = "title";
  private static final String PRICE = "price";
  private static final String CLICK_URL = "clickUrl";
  private static final String CALL_TO_ACTION = "callToAction";
  private static final String IMAGE = "image";
  private static final String DESCRIPTION = "description";
  private static final String NATIVE_URL = "url";
  private static final String HEIGHT = "height";
  private static final String WIDTH = "width";

  public String title;
  public String description;
  public String price;
  public String clickUrl;
  public String callToAction;
  public String imageUrl;
  public int imageHeight;
  public int imageWidth;

  public NativeProduct(JSONObject jsonProduct) throws JSONException {
    this.title = jsonProduct.optString(TITLE);
    this.description = jsonProduct.optString(DESCRIPTION);
    this.price = jsonProduct.optString(PRICE);
    this.clickUrl = jsonProduct.optString(CLICK_URL);
    this.callToAction = jsonProduct.optString(CALL_TO_ACTION);
    if (jsonProduct.has(IMAGE)) {
      this.imageUrl = jsonProduct.getJSONObject(IMAGE).optString(NATIVE_URL);
      this.imageHeight = jsonProduct.getJSONObject(IMAGE).optInt(HEIGHT);
      this.imageWidth = jsonProduct.getJSONObject(IMAGE).optInt(WIDTH);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof NativeProduct) {
      NativeProduct other = (NativeProduct) obj;
      return ((this.title == other.title || this.title.equals(other.title)) &&
          (this.description == other.description || this.description.equals(other.description)) &&
          (this.price == other.price || this.price.equals(other.price)) &&
          (this.clickUrl == other.clickUrl || this.clickUrl.equals(other.clickUrl)) &&
          (this.callToAction == other.callToAction || this.callToAction.equals(other.callToAction)) &&
          (this.imageUrl == other.imageUrl || this.imageUrl.equals(other.imageUrl)) &&
          this.imageHeight == other.imageHeight &&
          this.imageWidth == other.imageWidth);
    }
    return false;
  }
}
