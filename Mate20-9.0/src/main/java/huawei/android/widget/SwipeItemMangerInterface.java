package huawei.android.widget;

import huawei.android.widget.Attributes;
import java.util.List;

public interface SwipeItemMangerInterface {
    void closeAllExcept(SwipeLayout swipeLayout);

    void closeAllItems();

    void closeItem(int i);

    Attributes.Mode getMode();

    List<Integer> getOpenItems();

    List<SwipeLayout> getOpenLayouts();

    boolean isOpen(int i);

    void openItem(int i);

    void removeShownLayouts(SwipeLayout swipeLayout);

    void setMode(Attributes.Mode mode);
}
