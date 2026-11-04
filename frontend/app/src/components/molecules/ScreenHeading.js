import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors } from '../../theme/tokens';

export default function ScreenHeading({ title, subtitle, titleStyle, subtitleStyle }) {
  return (
    <View>
      <Text accessibilityRole="header" style={[styles.title, titleStyle]}>{title}</Text>
      {subtitle ? <Text style={[styles.subtitle, subtitleStyle]}>{subtitle}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  title: { fontSize: 20, fontWeight: '900', color: colors.foreground },
  subtitle: { fontSize: 12, color: colors.subtle, marginTop: 2, marginBottom: 16 },
});
