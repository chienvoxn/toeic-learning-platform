import React from 'react';
import { ActivityIndicator, TouchableOpacity } from 'react-native';

// Giữ nguyên style do caller cung cấp; bổ sung semantics chung cho thao tác.
export default function AppButton({ children, disabled, loading = false, accessibilityState, ...props }) {
  return (
    <TouchableOpacity
      activeOpacity={0.7}
      {...props}
      accessibilityRole={props.accessibilityRole || 'button'}
      accessibilityState={{ ...accessibilityState, disabled: !!(disabled || loading), busy: loading }}
      disabled={disabled || loading}
    >
      {loading ? <ActivityIndicator accessibilityLabel="Đang xử lý" /> : children}
    </TouchableOpacity>
  );
}
