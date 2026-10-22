package com.example.minijira.web;

import java.util.List;
import java.util.Optional;

public final class BugTemplates {

    public record Template(String id, String title, String hint, String summary, String description,
                           String priority, List<String> labels) {
    }

    public static final List<Template> ALL = List.of(
            new Template("npe",
                    "NullPointerException (stack trace'li)",
                    "Öncelik High → kural gereği AUTO_PR: düzeltme + test + PR beklenir.",
                    "Kuponsuz siparişlerde toplam hesaplanırken 500 hatası",
                    """
                    Kupon kullanmadan sipariş veren müşteriler ödeme adımında hata alıyor.

                    *Adımlar*
                    # POST /orders/total isteğine "coupon": null içeren bir sipariş gönder
                    # 500 Internal Server Error dönüyor

                    *Beklenen:* Kupon yoksa indirim 0 olmalı, toplam normal hesaplanmalı.

                    {noformat}
                    java.lang.NullPointerException: Cannot invoke "com.example.shop.order.Coupon.percentOff()" because "coupon" is null
                    	at com.example.shop.order.OrderService.discountFor(OrderService.java:37)
                    	at com.example.shop.order.OrderService.calculateTotal(OrderService.java:30)
                    	at com.example.shop.order.OrderController.total(OrderController.java:22)
                    	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
                    	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:258)
                    {noformat}
                    """,
                    "High",
                    List.of("ai-bugfix")),
            new Template("shipping",
                    "İş kuralı hatası (stack trace yok)",
                    "ai-auto-fix etiketi → AUTO_PR: sınır değer düzeltmesi beklenir.",
                    "Tam 50,00 € tutarındaki siparişlerden kargo ücreti alınıyor",
                    """
                    Kampanya kurallarına göre indirim sonrası tutarı *50,00 € ve üzeri* olan siparişlerde kargo ücretsiz.
                    Ancak sepet tutarı tam 50,00 € olduğunda müşteriden 4,99 € kargo ücreti alınıyor (toplam 54,99 €).

                    49,99 € → kargo alınmalı (doğru çalışıyor)
                    50,00 € → kargo ücretsiz olmalı (HATALI: 4,99 € alınıyor)
                    50,01 € → kargo ücretsiz (doğru çalışıyor)

                    Hesaplama OrderService içinde, FREE_SHIPPING_THRESHOLD sabitiyle yapılıyor.
                    """,
                    "Medium",
                    List.of("ai-bugfix", "ai-auto-fix")),
            new Template("vague",
                    "Belirsiz şikâyet (bilgi eksik)",
                    "Varsayılan mod ANALYZE_ONLY: agent'ın sorular sorması beklenir.",
                    "Bazı müşteriler fatura tutarının 1 kuruş farklı olduğunu söylüyor",
                    """
                    Müşteri hizmetlerine gelen birkaç şikâyette, e-postadaki tutar ile karttan çekilen tutar arasında
                    1 kuruş fark olduğu söyleniyor. Hangi siparişler olduğu bilinmiyor, tekrar üretilemedi.
                    """,
                    "Low",
                    List.of("ai-bugfix")));

    private BugTemplates() {
    }

    public static Optional<Template> byId(String id) {
        return ALL.stream().filter(t -> t.id().equals(id)).findFirst();
    }
}
