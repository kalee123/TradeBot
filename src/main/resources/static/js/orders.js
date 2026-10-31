function orderTime(timestamp) {
    // Kite sends "2026-11-01 09:15:32"; show just the time
    return timestamp ? String(timestamp).slice(11, 19) : "";
}

function statusClass(status) {
    if (status === "COMPLETE") return "pos";
    if (status === "REJECTED" || status === "CANCELLED") return "neg";
    return "muted";
}

loadPage(async () => {
    const orders = (await api("/api/orders")).slice().reverse();   // newest first

    document.getElementById("orders-body").innerHTML = tableRows(orders, 7, "No orders today", o => {
        const type = escapeHtml(o.transaction_type);
        const price = o.average_price || o.price;
        return `
            <tr>
                <td class="muted">${orderTime(o.order_timestamp)}</td>
                <td><span class="tag ${type.toLowerCase()}">${type}</span></td>
                <td><span class="symbol">${escapeHtml(o.tradingsymbol)}</span> <span class="muted">${escapeHtml(o.exchange)}</span></td>
                <td>${escapeHtml(o.product)}</td>
                <td class="num">${number(o.filled_quantity)} / ${number(o.quantity)}</td>
                <td class="num">${money(price)}</td>
                <td class="${statusClass(o.status)}" title="${escapeHtml(o.status_message)}">${escapeHtml(o.status)}</td>
            </tr>`;
    });
});
