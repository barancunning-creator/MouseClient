package com.mouseclient.render;

import java.util.ArrayList;
import java.util.List;

public class BedrockBone {
   public BedrockBone parent;
   public List<BedrockBone> children = new ArrayList<>();
   public List<BedrockCube> cubes = new ArrayList<>();
   public String name;
   public boolean hidden = false;
   public float pivotX;
   public float pivotY;
   public float pivotZ;
   private float rotationX;
   private float rotationY;
   private float rotationZ;
   private float offsetX;
   private float offsetY;
   private float offsetZ;
   private float scaleX = 1.0F;
   private float scaleY = 1.0F;
   private float scaleZ = 1.0F;

   public void setPivotY(float pivotY) {
      this.pivotY = pivotY;
   }

   public void setOffsetX(float offsetX) {
      this.offsetX = offsetX;
   }

   public BedrockBone(String name) {
      this.name = name;
   }

   public float getOffsetY() {
      return this.offsetY;
   }

   public void setPivotZ(float pivotZ) {
      this.pivotZ = pivotZ;
   }

   public float getPivotX() {
      return this.pivotX;
   }

   public float getScaleY() {
      return this.scaleY;
   }

   public void setOffsetZ(float offsetZ) {
      this.offsetZ = offsetZ;
   }

   public void setRotationX(float rotationX) {
      this.rotationX = rotationX;
   }

   public boolean isHidden() {
      return this.hidden;
   }

   public float getRotationZ() {
      return this.rotationZ;
   }

   public float getOffsetX() {
      return this.offsetX;
   }

   public float getPivotY() {
      return this.pivotY;
   }

   public void setScaleZ(float scaleZ) {
      this.scaleZ = scaleZ;
   }

   public void setRotationY(float rotationY) {
      this.rotationY = rotationY;
   }

   public void setRotationZ(float rotationZ) {
      this.rotationZ = rotationZ;
   }

   public void setScaleX(float scaleX) {
      this.scaleX = scaleX;
   }

   public float getRotationX() {
      return this.rotationX;
   }

   public void setHidden(boolean hidden) {
      this.hidden = hidden;
   }

   public float getRotationY() {
      return this.rotationY;
   }

   public float getScaleZ() {
      return this.scaleZ;
   }

   public String getName() {
      return this.name;
   }

   public float getOffsetZ() {
      return this.offsetZ;
   }

   public void setPivotX(float pivotX) {
      this.pivotX = pivotX;
   }

   public float getScaleX() {
      return this.scaleX;
   }

   public void setScaleY(float scaleY) {
      this.scaleY = scaleY;
   }

   public void setOffsetY(float offsetY) {
      this.offsetY = offsetY;
   }

   public float getPivotZ() {
      return this.pivotZ;
   }
}
