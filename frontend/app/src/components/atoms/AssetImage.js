import React, { useState } from 'react';
import { Image, StyleSheet, View } from 'react-native';
import { images } from '../../assets/images.generated';

// Thiếu ảnh: giữ fallback hiện có; không hotlink Figma hoặc require file chưa tồn tại.
export default function AssetImage({ assetId, style, imageStyle, fallback = null, resizeMode = 'cover', accessibilityLabel, collapseWhenMissing = false }) {
  const source = images[assetId];
  const [failedSource, setFailedSource] = useState(null);
  const available = source && source !== failedSource;
  if (!available && collapseWhenMissing) return null;
  return (
    <View style={style}>
      {available ? (
        <Image source={source} style={[styles.image, imageStyle]} resizeMode={resizeMode} accessibilityLabel={accessibilityLabel} accessible={!!accessibilityLabel} onError={() => setFailedSource(source)} />
      ) : fallback}
    </View>
  );
}

const styles = StyleSheet.create({ image: { width: '100%', height: '100%' } });
