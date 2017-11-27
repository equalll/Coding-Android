package net.coding.program.project.detail;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;

import com.loopj.android.http.RequestParams;

import net.coding.program.R;
import net.coding.program.route.BlankViewDisplay;
import net.coding.program.common.DialogUtil;
import net.coding.program.common.Global;
import net.coding.program.route.GlobalCommon;
import net.coding.program.common.ImageLoadTool;
import net.coding.program.common.LoadMore;
import net.coding.program.common.network.NetworkImpl;
import net.coding.program.common.ImageInfo;
import net.coding.program.pickphoto.PhotoPickActivity;
import net.coding.program.common.umeng.UmengEvent;
import net.coding.program.common.util.BlankViewHelp;
import net.coding.program.common.util.FileUtil;
import net.coding.program.common.util.PermissionUtil;
import net.coding.program.common.widget.BottomToolBar;
import net.coding.program.common.widget.FileListHeadItem;
import net.coding.program.model.AttachmentFileObject;
import net.coding.program.model.AttachmentFolderObject;
import net.coding.program.model.ProjectObject;
import net.coding.program.project.detail.file.FileDownloadBaseActivity;
import net.coding.program.project.detail.file.ViewHolderFile;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.ItemClick;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.OptionsItem;
import org.androidannotations.annotations.ViewById;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.Header;
import top.zibin.luban.Luban;
import top.zibin.luban.OnCompressListener;

import static net.coding.program.common.Global.PHOTO_MAX_COUNT;

/**
 * Created by yangzhen
 */
@Deprecated
@EActivity(R.layout.activity_attachments)
public class AttachmentsActivity extends FileDownloadBaseActivity implements LoadMore, UploadStyle {

    public static final int RESULT_REQUEST_FILES = 1;
    private static final String TAG = AttachmentsActivity.class.getSimpleName();

    public static final int RESULT_REQUEST_PICK_PHOTO = 1003;
    final public static int FILE_SELECT_CODE = 10;
    final public static int FILE_DELETE_CODE = 11;
    final public static int FILE_MOVE_CODE = 12;
    final public static int RESULT_MOVE_FOLDER = 13;
    private static final String TAG_HTTP_FILE_EXIST = "TAG_HTTP_FILE_EXIST";
    private static final String HOST_HTTP_FILE_RENAME = "HOST_HTTP_FILE_RENAME";

    public final String HOST_PROJECT_ID = Global.HOST_API + "/project/%s";
    private final String TAG_MOVE_FOLDER = "TAG_MOVE_FOLDER";

    @Extra
    int mProjectObjectId;
    @Extra
    ProjectObject mProject;
    @Extra
    AttachmentFolderObject mAttachmentFolderObject;

    //    ProjectObject mProjectObject;
    String urlFiles = "";
    String urlUpload = Global.HOST_API + "/project/%s/file/upload";
    ArrayList<AttachmentFileObject> mFilesArray = new ArrayList<>();
    protected CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            AttachmentFileObject data = mFilesArray.get((Integer) buttonView.getTag());
            data.isSelected = isChecked;
        }
    };

    boolean mNoMore = false;

    @ViewById
    protected SwipeRefreshLayout swipeRefreshLayout;

    @ViewById
    ListView listView;
    //    @ViewById
//    RelativeLayout uploadLayout;
    //var EDITABLE_FILE_REG=/\.(txt|md|html|htm)$/
    // /\.(pdf)$/
    @ViewById
    View blankLayout;

    @ViewById
    BottomToolBar bottomLayout, bottomLayoutBatch;

    ActionMode mActionMode;
    ArrayList<AttachmentFileObject> selectFile;
    ArrayList<AttachmentFolderObject> selectFolder;
    //    private AttachmentFolderObject sourceFolder;
    View.OnClickListener mClickReload = v -> loadMore();
    ViewGroup listHead;

    // 获取所有文件夹
    private String HOST_FOLDER = Global.HOST_API + "/project/%s/all_folders?pageSize=9999";
    // 获取分享中文件数量
    private String HOST_SHARE_FOLDER_COUNT = Global.HOST_API + "/project/%s/folders/all-file-count-with-share";

    private String HOST_FILE_DELETE = Global.HOST_API + "/project/%s/file/delete?%s";
    private String HOST_FILE_MOVETO = Global.HOST_API + "/project/%s/files/moveto/%s?%s";
    private String HOST_FILECOUNT = Global.HOST_API + "/project/%s/folders/all_file_count";
    private String HOST_FOLDER_NAME = Global.HOST_API + "/project/%s/dir/%s/name/%s";
    private String HOST_FOLDER_NEW = Global.HOST_API + "/project/%s/mkdir";
    private String HOST_FOLDER_DELETE_FORMAT = Global.HOST_API + "/project/%s/rmdir/%s";
    private String HOST_FOLDER_DELETE;
    private HashMap<String, Integer> fileCountMap = new HashMap<>();
    private boolean isEditMode = false;

    BaseAdapter adapter = new BaseAdapter() {

        @Override
        public int getCount() {
            return mFilesArray.size();
        }

        @Override
        public Object getItem(int position) {
            return mFilesArray.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolderFile holder;
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.project_attachment_file_list_item, parent, false);
                holder = new ViewHolderFile(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolderFile) convertView.getTag();
            }
            AttachmentFileObject data = mFilesArray.get(position);
            holder.name.setText(data.getName());

            int itemHigh;
            if (data.isFolder) {
                itemHigh = GlobalCommon.dpToPx(65);
            } else {
                itemHigh = GlobalCommon.dpToPx(85);
            }
            ViewGroup.LayoutParams lp = holder.item_layout_root.getLayoutParams();
            lp.height = itemHigh;
            holder.item_layout_root.setLayoutParams(lp);

            if (data.isFolder) {
                int folderDrawable = R.drawable.ic_project_git_folder2;
                if (data.file_id.equals(AttachmentFolderObject.SHARE_FOLDER_ID)) {
                    folderDrawable = R.drawable.icon_file_folder_share;
                } else if (data.file_id.equals(AttachmentFolderObject.DEFAULT_FOLDER_ID)) {
                    folderDrawable = R.drawable.ic_project_git_folder;
                }
                holder.icon.setImageResource(folderDrawable);
                holder.icon.setVisibility(View.VISIBLE);
                holder.icon.setBackgroundResource(android.R.color.transparent);
                holder.file_info_layout.setVisibility(View.GONE);
                holder.folder_name.setText(data.getName());
                holder.folder_name.setVisibility(View.VISIBLE);
            } else if (data.isImage()) {
                //Log.d("imagePattern", "data.preview:" + data.preview);
                imagefromNetwork(holder.icon, data.preview, ImageLoadTool.optionsRounded2);
                holder.icon.setVisibility(View.VISIBLE);
                holder.icon.setBackgroundResource(R.drawable.shape_image_icon_bg);
                holder.file_info_layout.setVisibility(View.VISIBLE);
                holder.folder_name.setVisibility(View.GONE);
            } else {
                imagefromNetwork(holder.icon, "drawable://" + data.getIconResourceId(), ImageLoadTool.optionsRounded2);
                holder.icon.setVisibility(View.VISIBLE);
                holder.icon.setBackgroundResource(android.R.color.transparent);
                holder.file_info_layout.setVisibility(View.VISIBLE);
                holder.folder_name.setVisibility(View.GONE);
            }

            holder.content.setText(Global.HumanReadableFilesize(data.getSize()));
            holder.desc.setText(String.format("发布于%s", Global.dayToNow(data.created_at)));
            holder.username.setText(data.owner.name);


            if (data.isShared()) {
                holder.shareMark.setVisibility(View.VISIBLE);
            } else {
                holder.shareMark.setVisibility(View.INVISIBLE);
            }

            if (position == mFilesArray.size() - 1) {
                if (!mNoMore) {
                    loadMore();
                }

                holder.bottomLine.setVisibility(View.INVISIBLE);
            } else {
                holder.bottomLine.setVisibility(View.VISIBLE);
            }

            holder.checkBox.setTag(position);
            if (isEditMode) {
                if (!data.isFolder)
                    holder.checkBox.setVisibility(View.VISIBLE);
                else
                    holder.checkBox.setVisibility(View.INVISIBLE);

                if (data.isSelected) {
                    holder.checkBox.setChecked(true);
                } else {
                    holder.checkBox.setChecked(false);
                }
            } else {
                holder.checkBox.setVisibility(View.GONE);
            }
            holder.checkBox.setOnCheckedChangeListener(onCheckedChangeListener);


            if (data.bytesAndStatus != null) {
                Log.v("updateFileDownload", data.getName() + ":" + data.bytesAndStatus[0] + " " + data.bytesAndStatus[1] + " " + data.bytesAndStatus[2]);
            }

            if (data.downloadId != 0L) {
                int status = data.bytesAndStatus[2];
                if (AttachmentsDownloadDetailActivity.isDownloading(status)) {
                    if (data.bytesAndStatus[1] < 0) {
                        holder.progressBar.setProgress(0);
                    } else {
                        holder.progressBar.setProgress(data.bytesAndStatus[0] * 100 / data.bytesAndStatus[1]);
                    }
                    data.isDownload = false;
                    holder.desc_layout.setVisibility(View.GONE);
                    holder.more.setVisibility(View.GONE);
                    holder.progress_layout.setVisibility(View.VISIBLE);
                    holder.downloadFlag.setText("取消");
                } else {
                    if (status == DownloadManager.STATUS_FAILED) {
                        data.isDownload = false;
                    } else if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        data.isDownload = true;
                        downloadFileSuccess(data.file_id);
                    } else {
                        data.isDownload = false;
                    }

                    data.downloadId = 0L;

                    holder.desc_layout.setVisibility(View.VISIBLE);
                    holder.more.setVisibility(View.VISIBLE);
                    holder.progress_layout.setVisibility(View.GONE);
                    holder.downloadFlag.setText(data.isDownload ? "查看" : "下载");
                }
            } else {
                holder.desc_layout.setVisibility(View.VISIBLE);
                holder.more.setVisibility(View.VISIBLE);
                holder.progress_layout.setVisibility(View.GONE);
                holder.downloadFlag.setText(data.isDownload ? "查看" : "下载");
            }

            holder.more.setTag(position);
            holder.more.setOnClickListener(onMoreClickListener);
            holder.item_layout_root.setBackgroundResource(data.isDownload
                    ? R.drawable.list_item_selector_project_file
                    : R.drawable.list_item_selector);

            if (data.isFolder) {
                holder.more.setVisibility(View.INVISIBLE);
            } else {
                holder.more.setVisibility(View.VISIBLE);
            }

            return convertView;
        }
    };

    /**
     * 弹出框
     */
    private DialogUtil.BottomPopupWindow mAttachmentPopupWindow = null;//文件目录的底部弹出框
    private DialogUtil.BottomPopupWindow mAttachmentFilePopupWindow = null;//文件文件的底部弹出框
    private int selectedPosition;
    protected View.OnClickListener onMoreClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Integer position = (Integer) view.getTag();
            AttachmentFileObject file = mFilesArray.get(position);

            selectedPosition = position;
            if (file.isDownload) {
                listViewItemClicked(file);
            } else {
                if (file.bytesAndStatus != null && file.bytesAndStatus[1] < 0) {
                    long downloadId = file.downloadId;
                    removeDownloadFile(downloadId);
                    file.downloadId = 0L;
                    adapter.notifyDataSetChanged();
                } else {
                    action_download_single(mFilesArray.get(selectedPosition));
                }
            }
        }
    };
    private AdapterView.OnItemClickListener onPopupItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
            switch (position) {
                case 0:
                    doRename(selectedPosition, mFilesArray.get(selectedPosition).folderObject);
                    break;
                case 1:
                    AttachmentFolderObject selectedFolderObject = mFilesArray.get(selectedPosition).folderObject;
                    if (selectedFolderObject.isDeleteable()) {
                        action_delete_single(selectedFolderObject);
                    } else {
                        showButtomToast("请先清空文件夹");
                        return;
                    }
                    break;
            }
            mAttachmentPopupWindow.dismiss();
        }
    };
    private AdapterView.OnItemClickListener onFilePopupItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
            switch (position) {
                case 0:
                    // 移动
                    action_move_single(mFilesArray.get(selectedPosition));
                    break;
                case 1:
                    // 下载
                    action_download_single(mFilesArray.get(selectedPosition));
                    break;
                case 2:
                    // 删除
                    action_delete_single(mFilesArray.get(selectedPosition));
                    break;
            }
            mAttachmentFilePopupWindow.dismiss();
        }
    };
    /**
     * 为了实现设计的样式，右上角下拉没有用actionbar自带的，而是用了PopupWindow
     */
    private DialogUtil.RightTopPopupWindow mRightTopPopupWindow = null;
    private AdapterView.OnItemClickListener onRightTopPopupItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
            switch (position) {
                case 0:
                    action_move();
                    break;
                case 1:
                    action_download(mFilesArray);
                    break;
                case 2:
                    if (isChooseOthers()) {
                        return;
                    } else {
                        action_delete();
                    }

                    break;
            }
            mRightTopPopupWindow.dismiss();
        }
    };

    protected void onRefresh() {
        initSetting();

        if (mAttachmentFolderObject.isRoot()) {
            getNetwork(HOST_SHARE_FOLDER_COUNT, HOST_SHARE_FOLDER_COUNT);
        } else if (mAttachmentFolderObject.isDefault() || mAttachmentFolderObject.isSharded()) {
            loadFileList();
        } else if (mAttachmentFolderObject.created_at == 0) {
            loadFolderList();
        } else {
            loadFileList();
        }
    }

    protected void loadFolderList() {
        getNetwork(HOST_FOLDER, HOST_FOLDER);
    }

    protected void loadFileList() {
        initSetting();
        loadMore();
    }

    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.project_attachment_file_edit, menu);

            bottomLayoutBatch.setVisibility(View.VISIBLE);
            bottomLayout.setVisibility(View.GONE);

            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;// Return falsei f nothing is done
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_all:
                    action_all();
                    return true;
                case R.id.action_inverse:
                    action_inverse();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;

            bottomLayoutBatch.setVisibility(View.GONE);
            showFolderAction();

            setListEditMode(false);
        }
    };
    private List<String> tags = new ArrayList<>();
    private boolean isUpload = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventUpdate(String name) {
        if (!TextUtils.isEmpty(name) && name.equals(mAttachmentFolderObject.name)) {
            initSetting();
            loadMore();
        }
    }

    private void showFolderAction() {
        if (mAttachmentFolderObject.file_id.equals(AttachmentFolderObject.SHARE_FOLDER_ID)) {
            bottomLayout.setVisibility(View.GONE);
        } else {
            bottomLayout.setVisibility(View.VISIBLE);
        }
    }

    @OptionsItem(android.R.id.home)
    void close() {
        onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();

        if (!mAttachmentFolderObject.isRoot()) {
            inflater.inflate(R.menu.attachment_list, menu);
        }

        return true;
    }

    @AfterViews
    final void initAttachmentsActivity() {
//        uploadLayout.setVisibility(View.GONE);
        swipeRefreshLayout.setOnRefreshListener(this::onRefresh);

        swipeRefreshLayout.setColorSchemeResources(R.color.font_green);

        setActionBarTitle(mAttachmentFolderObject.name);

        if (mAttachmentFolderObject.file_id.equals(AttachmentFolderObject.SHARE_FOLDER_ID)) {
            String template = Global.HOST_API + "/project/%s/shared_files?height=90&width=90&pageSize=9999";
            urlFiles = String.format(template, mProjectObjectId);
        } else {
            String template = Global.HOST_API + "/project/%s/files/%s?height=90&width=90&pageSize=9999";
            urlFiles = String.format(template, mProjectObjectId, mAttachmentFolderObject.file_id);
        }
        HOST_FOLDER = String.format(HOST_FOLDER, mProjectObjectId);
        HOST_SHARE_FOLDER_COUNT = String.format(HOST_SHARE_FOLDER_COUNT, mProjectObjectId);

        urlUpload = String.format(urlUpload, mProjectObjectId);
//        barParams = (LinearLayout.LayoutParams) uploadStatusProgress.getLayoutParams();
//        barParamsRemain = (LinearLayout.LayoutParams) uploadStatusProgressRemain.getLayoutParams();

        HOST_FILECOUNT = String.format(HOST_FILECOUNT, mProjectObjectId);

        listViewAddHeaderSection(listView);
        listHead = (ViewGroup) getLayoutInflater().inflate(R.layout.upload_file_layout, listView, false);
        listView.addHeaderView(listHead, null, false);
        listViewAddFootSection(listView);
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            showPop(null, (int) id);
            return true;
        });

        initBottomPop();

        String projectUrl = String.format(HOST_PROJECT_ID, mProjectObjectId);
        getNetwork(projectUrl, HOST_PROJECT_ID);

        showFolderAction();

        bottomLayoutBatch.setClick(clickBottom);
        bottomLayout.setClick(clickBottom);

        onRefresh();

        initBottomToolBar();

        BlankViewHelp.setBlankLoading(blankLayout, true);
    }

    private void initBottomToolBar() {
        if (mAttachmentFolderObject.isRoot()) {
            bottomLayout.disable(R.id.actionUpload);
        } else if (mAttachmentFolderObject.isSharded()) {
            bottomLayout.setVisibility(View.GONE);
        } else if (mAttachmentFolderObject.isDefault()) {
            bottomLayout.disable(R.id.actionAddFolder);
        } else if (!mAttachmentFolderObject.parent_id.equals("0")) {
            bottomLayout.disable(R.id.actionAddFolder);
        }
    }

    View.OnClickListener clickBottom = v -> {
        switch (v.getId()) {
            case R.id.actionAddFolder:
                common_folder_bottom_add();
                break;
            case R.id.actionUpload:
                common_folder_bottom_upload();
                break;
            case R.id.filesMove:
                common_files_move();
                break;
            case R.id.filesDownload:
                common_files_download();
                break;
            case R.id.filesDelete:
                common_files_delete();
                break;
        }
    };

    void common_files_move() {
        action_move();
    }

    void common_files_download() {
        action_download(mFilesArray);
    }

    void common_files_delete() {
        if (!isChooseOthers()) {
            action_delete();
        } else {
            showMiddleToast("不能删除别人的文件");
        }
    }

    @ItemClick
    void listViewItemClicked(AttachmentFileObject fileObject) {
        AttachmentFileObject data = fileObject;

        if (isEditMode) {
            if (!data.isFolder) {
                data.isSelected = !data.isSelected;
                adapter.notifyDataSetChanged();
            }
        } else {
            if (data.isFolder) {
                AttachmentsActivity_.intent(AttachmentsActivity.this)
                        .mAttachmentFolderObject(data.folderObject).
                        mProjectObjectId(mProjectObjectId)
                        .mProject(mProject)
                        .startForResult(RESULT_REQUEST_FILES);
//            } else if (data.isImage()) {
//                AttachmentsPicDetailActivity_.intent(AttachmentsActivity.this).mProjectObjectId(mProjectObjectId).mAttachmentFolderObject(mAttachmentFolderObject).mAttachmentFileObject(data).fileList(getPicFiles()).startForResult(FILE_DELETE_CODE);
//                    } else if (data.isHtml() || data.isMd()) {
//                        AttachmentsHtmlDetailActivity_.intent(AttachmentsActivity.this).mProjectObjectId(mProjectObjectId).mAttachmentFolderObject(mAttachmentFolderObject).mAttachmentFileObject(data).startForResult(FILE_DELETE_CODE);
//                    } else if (data.isTxt()) {
//                        AttachmentsTextDetailActivity_.intent(AttachmentsActivity.this).mProjectObjectId(mProjectObjectId).mAttachmentFolderObject(mAttachmentFolderObject).mAttachmentFileObject(data).startForResult(FILE_DELETE_CODE);
            } else {
                if (data.isDownload) {
                    jumpToDetail(data);
                } else if (data.isImage()) {
                    AttachmentsPhotoDetailActivity_
                            .intent(this)
                            .mProjectObjectId(mProjectObjectId)
                            .mAttachmentFolderObject(mAttachmentFolderObject)
                            .mAttachmentFileObject(data)
                            .mProject(mProject)
                            .startForResult(FILE_DELETE_CODE);
                } else {
                    AttachmentsDownloadDetailActivity_.intent(AttachmentsActivity.this)
                            .mProjectObjectId(mProjectObjectId)
                            .mAttachmentFolderObject(mAttachmentFolderObject)
                            .mAttachmentFileObject(data)
                            .mProject(mProject)
                            .startForResult(FILE_DELETE_CODE);
                }
            }
        }
    }

    private void jumpToDetail(AttachmentFileObject data) {
        if (AttachmentFileObject.isTxt(data.fileType)) {
            AttachmentsTextDetailActivity_
                    .intent(this)
                    .mProjectObjectId(mProjectObjectId)
                    .mAttachmentFolderObject(mAttachmentFolderObject)
                    .mAttachmentFileObject(data)
                    .mProject(mProject)
                    .startForResult(FILE_DELETE_CODE);

        } else if (AttachmentFileObject.isMd(data.fileType)) {
            AttachmentsHtmlDetailActivity_
                    .intent(this)
                    .mProjectObjectId(mProjectObjectId)
                    .mAttachmentFolderObject(mAttachmentFolderObject)
                    .mAttachmentFileObject(data)
                    .mProject(mProject)
                    .startForResult(FILE_DELETE_CODE);

        } else if (data.isImage()) {
            AttachmentsPhotoDetailActivity_
                    .intent(this)
                    .mProjectObjectId(mProjectObjectId)
                    .mAttachmentFolderObject(mAttachmentFolderObject)
                    .mAttachmentFileObject(data)
                    .mProject(mProject)
                    .startForResult(FILE_DELETE_CODE);
        } else {
            AttachmentsDownloadDetailActivity_.intent(AttachmentsActivity.this)
                    .mProjectObjectId(mProjectObjectId)
                    .mAttachmentFolderObject(mAttachmentFolderObject)
                    .mAttachmentFileObject(data)
                    .mProject(mProject)
                    .startForResult(FILE_DELETE_CODE);
        }
    }

    @Override
    public void loadMore() {
        if (mAttachmentFolderObject.isRoot()) {
            return;
        }

        getNextPageNetwork(urlFiles, urlFiles);
    }

    @Override
    public void parseJson(int code, JSONObject respanse, String tag, int pos, final Object data) throws JSONException {
        if (tag.equals(HOST_FOLDER)) {
            swipeRefreshLayout.setRefreshing(false);
            if (code == 0) {
                JSONArray folders = respanse.getJSONObject("data").getJSONArray("list");

                mFilesArray.clear();

                if (mAttachmentFolderObject.isRoot()) { // 根目录只有文件夹

                    AttachmentFolderObject shareFolder = new AttachmentFolderObject();
                    shareFolder.file_id = AttachmentFolderObject.SHARE_FOLDER_ID;
                    shareFolder.setCount(fileCountMap.get(shareFolder.file_id));
                    shareFolder.name = "分享中";
                    mFilesArray.add(AttachmentFileObject.parseFileObject(shareFolder));

                    AttachmentFolderObject defaultFolder = new AttachmentFolderObject();
                    defaultFolder.setCount(fileCountMap.get(defaultFolder.file_id));
                    mFilesArray.add(AttachmentFileObject.parseFileObject(defaultFolder));
                }

                for (int i = 0; i < folders.length(); ++i) {
                    AttachmentFolderObject folder = new AttachmentFolderObject(folders.getJSONObject(i));
                    folder.setCount(fileCountMap.get(folder.file_id));
                    ArrayList<AttachmentFolderObject> subFolders = folder.sub_folders;
                    for (AttachmentFolderObject subFolder : subFolders) {
                        subFolder.setCount(fileCountMap.get(subFolder.file_id));
                    }
                    mFilesArray.add(AttachmentFileObject.parseFileObject(folder));
                }

                if (mAttachmentFolderObject.isRoot()) {
                    adapter.notifyDataSetChanged();
                    BlankViewHelp.setBlankLoading(blankLayout, false);
                } else {
                    loadMore();
                }
            } else {
                showErrorMsg(code, respanse);
            }
        } else if (tag.equals(HOST_SHARE_FOLDER_COUNT)) {
            if (code == 0) {
                JSONObject dataRoot = respanse.optJSONObject("data");

                fileCountMap.put(AttachmentFolderObject.SHARE_FOLDER_ID, dataRoot.optInt("shareCount"));
                JSONArray counts = dataRoot.optJSONArray("folders");
                for (int i = 0; i < counts.length(); ++i) {
                    JSONObject countItem = counts.optJSONObject(i);
                    fileCountMap.put(countItem.optString("folder"), countItem.optInt("count"));
                }
                loadFolderList();
            } else {
                showErrorMsg(code, respanse);
            }
        } else if (tag.equals(urlFiles)) {
            swipeRefreshLayout.setRefreshing(false);

            if (code == 0) {
                JSONArray files = respanse.getJSONObject("data").getJSONArray("list");

                if (isLoadingFirstPage(urlFiles)) {
                    mFilesArray.clear();
                }

                if (mFilesArray.size() == 0) {

                    ArrayList<AttachmentFolderObject> subFolders = mAttachmentFolderObject.sub_folders;
                    for (AttachmentFolderObject subFolder : subFolders) {
                        mFilesArray.add(AttachmentFileObject.parseFileObject(subFolder));
                    }
                }

                for (int i = 0; i < files.length(); ++i) {
                    AttachmentFileObject fileObject = new AttachmentFileObject(files.getJSONObject(i));

                    setDownloadStatus(fileObject);
                    mFilesArray.add(fileObject);
                }


                int page = respanse.getJSONObject("data").optInt("page");
                int totalPage = respanse.getJSONObject("data").optInt("totalPage");
                if (page == totalPage)
                    mNoMore = true;
                adapter.notifyDataSetChanged();

            } else {
                showErrorMsg(code, respanse);
            }

            BlankViewDisplay.setBlank(mFilesArray.size(), this, code == 0, blankLayout, mClickReload);

        } else if (tag.equals(HOST_FILE_DELETE)) {
            if (code == 0) {
                umengEvent(UmengEvent.FILE, "删除文件");

                hideProgressDialog();
                showButtomToast("删除完成");
                mFilesArray.removeAll(selectFile);
                adapter.notifyDataSetChanged();
                setResult(Activity.RESULT_OK);
            } else {
                showErrorMsg(code, respanse);
            }

            BlankViewDisplay.setBlank(mFilesArray.size(), this, code == 0, blankLayout, mClickReload);

        } else if (tag.equals(HOST_FILE_MOVETO) || tag.equals(TAG_MOVE_FOLDER)) {
            if (code == 0) {
                umengEvent(UmengEvent.FILE, "移动文件夹");

                showButtomToast("移动成功");

                if (data instanceof AttachmentFolderObject) {
                    AttachmentFolderObject folder = (AttachmentFolderObject) data;
                    EventBus.getDefault().post(folder.name);
                }

                mFilesArray.removeAll(selectFile);
                adapter.notifyDataSetChanged();
                setResult(Activity.RESULT_OK);
            } else {
                showErrorMsg(code, respanse);
            }
            BlankViewDisplay.setBlank(mFilesArray.size(), this, code == 0, blankLayout, mClickReload);
        } else if (tag.equals(HOST_FILECOUNT)) {
            if (code == 0) {
                JSONArray counts = respanse.getJSONArray("data");

                for (int i = 0; i < counts.length(); ++i) {
                    JSONObject countItem = counts.optJSONObject(i);
                    fileCountMap.put(countItem.optString("folder"), countItem.optInt("count"));
                }

                for (AttachmentFileObject fileObject : mFilesArray) {
                    if (fileObject.isFolder) {
                        fileObject.folderObject.setCount(fileCountMap.get(fileObject.folderObject.file_id));
                        fileObject.setName(fileObject.folderObject.getNameCount());
                    }
                }
                adapter.notifyDataSetChanged();

            } else {
                showErrorMsg(code, respanse);
            }
        } else if (tag.equals(HOST_FOLDER_NAME)) {
            if (code == 0) {
                umengEvent(UmengEvent.FILE, "重命名文件夹");
                showButtomToast("重命名成功");
                AttachmentFileObject folderObject = mFilesArray.get(pos);

                folderObject.folderObject.name = (String) data;
                folderObject.setName(folderObject.folderObject.getNameCount());
                adapter.notifyDataSetChanged();
                //mData.clear();
                //AttachmentFolderObject folderObject = (AttachmentFolderObject)data;
                //loadMore();
            } else {
                showButtomToast("重命名失败");
            }
        } else if (tag.equals(HOST_HTTP_FILE_RENAME)) {
            if (code == 0) {
                umengEvent(UmengEvent.FILE, "重命名文件");

                showButtomToast("重命名成功");
                AttachmentFileObject folderObject = mFilesArray.get(pos);
                folderObject.setName((String) data);
                adapter.notifyDataSetChanged();

            } else {
                showButtomToast("重命名失败");
            }
        } else if (tag.equals(HOST_FOLDER_DELETE)) {
            if (code == 0) {
                umengEvent(UmengEvent.FILE, "删除文件夹");
                //setRefreshing(false);
                AttachmentFileObject folderObject = mFilesArray.get(pos);
                mFilesArray.remove(pos);
                selectFolder.remove(0);
                if (selectFolder.size() > 0) {
                    deleteFolders();
                } else {
                    showButtomToast("删除完成");
                    adapter.notifyDataSetChanged();
                }
                setResult(Activity.RESULT_OK);
            } else {
                showErrorMsg(code, respanse);
            }

            BlankViewDisplay.setBlank(mFilesArray.size(), this, code == 0, blankLayout, mClickReload);
        } else if (tag.equals(HOST_FOLDER_NEW)) {
            if (code == 0) {
                umengEvent(UmengEvent.FILE, "新建文件夹");
                AttachmentFolderObject folder = new AttachmentFolderObject(respanse.getJSONObject("data"));
                mAttachmentFolderObject.sub_folders.add(0, folder);
                mFilesArray.add(0, AttachmentFileObject.parseFileObject(folder));
                adapter.notifyDataSetChanged();
                setResult(Activity.RESULT_OK);
            } else {
                showErrorMsg(code, respanse);
            }
            BlankViewDisplay.setBlank(mFilesArray.size(), this, code == 0, blankLayout, mClickReload);

        } else if (tag.equals(HOST_PROJECT_ID)) {
            if (code == 0) {
                mProject = new ProjectObject(respanse.optJSONObject("data"));
            } else {
                showErrorMsg(code, respanse);
            }
        } else if (tag.equals(TAG_HTTP_FILE_EXIST)) {
            umengEvent(UmengEvent.FILE, "上传文件");
            if (code == 0) {
                String s = respanse.optJSONObject("data").optString("conflict_file");
                if (s.isEmpty()) {
                    uploadFile((File) data);
                } else {
                    File file = (File) data;
                    showDialog(file.getName(), "存在同名文件，是否覆盖？", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            uploadFile((File) data);
                        }
                    });
                }
            } else {
                showErrorMsg(code, respanse);
            }
        }

        if (isUpload) {
            for (String fileTag : tags) {
                if (tag.equals(fileTag)) {
                    String s = respanse.optJSONObject("data").optString("conflict_file");
                    if (s.isEmpty()) {
                        uploadFile((File) data);
                    } else {
                        File file = (File) data;
                        showDialog(file.getName(), "存在同名文件，是否覆盖？", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                uploadFile((File) data);
                            }
                        });
                    }
                } else {
                    showErrorMsg(code, respanse);
                }
            }
        }
    }

    protected final void common_folder_bottom_upload() {
        showListDialog();
    }

    private void showListDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MyAlertDialogStyle);
        builder.setItems(R.array.file_type, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        startPhotoPickActivity();
                        break;
                    case 1:
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("*/*");
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        try {
                            startActivityForResult(Intent.createChooser(intent, "请选择一个要上传的文件"),
                                    FILE_SELECT_CODE);
                        } catch (android.content.ActivityNotFoundException ex) {
                            showButtomToast("请安装文件管理器");
                        }
                        break;
                    default:
                        break;
                }
            }
        }).show();
    }

    private void startPhotoPickActivity() {
        if (!PermissionUtil.writeExtralStorage(this)) {
            return;
        }

        Intent intent = new Intent(this, PhotoPickActivity.class);
        intent.putExtra(PhotoPickActivity.EXTRA_MAX, PHOTO_MAX_COUNT);
        startActivityForResult(intent, RESULT_REQUEST_PICK_PHOTO);
    }

    @OnActivityResult(FILE_SELECT_CODE)
    void onResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();
            String path = FileUtil.getPath(this, uri);
            File selectedFile = new File(path);
            uploadFilePrepare(selectedFile);
        }
    }

    @OnActivityResult(RESULT_REQUEST_PICK_PHOTO)
    void onPickResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            try {
                @SuppressWarnings("unchecked")
                ArrayList<ImageInfo> pickPhots = (ArrayList<ImageInfo>) data.getSerializableExtra("data");
                List<String> photos = new ArrayList<>();
                for (ImageInfo item : pickPhots) {
                    photos.add(item.getPath());
                }

                List<File> zipPhotos = new ArrayList<>();
                Luban.with(this)
                        .load(photos)                                   // 传人要压缩的图片列表
                        .ignoreBy(100)                                  // 忽略不压缩图片的大小
                        .setTargetDir(getCacheDir().getPath())                        // 设置压缩后文件存储位置
                        .setCompressListener(new OnCompressListener() { //设置回调

                            int zipCount = 0;

                            @Override
                            public void onStart() {
                            }

                            @Override
                            public void onSuccess(File file) {
                                zipPhotos.add(file);

                                zipCount++;
                                if (zipCount >= photos.size()) {
                                    uploadFilePrepareList(zipPhotos);
                                }
                            }

                            @Override
                            public void onError(Throwable e) {
                                zipCount++;
                            }

                        }).launch();    //启动压缩

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void uploadFilePrepare(File selectedFile) {
        String httpHost = getHttpFileExist(selectedFile.getName(), mAttachmentFolderObject);
        getNetwork(httpHost, TAG_HTTP_FILE_EXIST, -1, selectedFile);
    }

    private void uploadFilePrepareList(List<File> files) {
        isUpload = true;
        tags.clear();
        for (File file : files) {
            String tag = file.getName();
            tags.add(tag);
            String httpHost = getHttpFileExist(file.getName(), mAttachmentFolderObject);
            getNetwork(httpHost, file.getName(), -1, file);
        }
    }

    private void uploadFile(File selectedFile) {
//        showUploadStatus(UploadStatus.Uploading);

        FileListHeadItem uploadItemView = new FileListHeadItem(AttachmentsActivity.this);
        listHead.addView(uploadItemView);

        FileListHeadItem.Param param = new FileListHeadItem.Param(urlUpload,
                mAttachmentFolderObject.file_id, selectedFile);
        uploadItemView.setData(param, this, getImageLoad());

//            client.setTimeout(60 * 60 * 1000); // 超时设为60分钟


    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
        if (AttachmentsActivity.this.isFinishing()) {
            return;
        }
        Log.v(TAG, "onSuccess");
        try {
            int code = response.getInt("code");

            if (code == 0) {
                umengEvent(UmengEvent.FILE, "上传文件");
                AttachmentFileObject newFile = new AttachmentFileObject(response.getJSONObject("data"));
                setDownloadStatus(newFile);

                int i = 0;
                for (; i < mFilesArray.size(); ++i) {
                    AttachmentFileObject item = mFilesArray.get(i);
                    String itemName = item.getName();
                    if (!item.isFolder && itemName.equals(newFile.getName())) {
                        mFilesArray.set(i, newFile);
                        break;
                    }
                }
                if (i == mFilesArray.size()) {
                    mFilesArray.add(mAttachmentFolderObject.sub_folders.size(), newFile);
                }

                adapter.notifyDataSetChanged();
                setResult(Activity.RESULT_OK);
//                showUploadStatus(UploadStatus.Finish);

            } else {
                showErrorMsg(code, response);
            }

            BlankViewDisplay.setBlank(mFilesArray.size(), this, code == 0, blankLayout, mClickReload);

        } catch (Exception e) {
            Global.errorLog(e);
        }
    }

    @Override
    public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
        Log.v(TAG, "onFailure");
        try {
            showErrorMsg(NetworkImpl.NETWORK_ERROR, errorResponse);
//            showUploadStatus(UploadStatus.Failure);
        } catch (Exception e) {
            Global.errorLog(e);
        }
    }

    private String getHttpFileExist(String name, AttachmentFolderObject folder) {
        String encodeName = Global.encodeUtf8(name);
        return Global.HOST_API +
                mProject.getProjectPath() +
                "/dir/" +
                folder.file_id +
                "/files/existed?names=" +
                encodeName;
    }

    @OnActivityResult(FILE_DELETE_CODE)
    void onFileResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            int actionName = data.getIntExtra(FileActions.ACTION_NAME, 0);
            switch (actionName) {
                case FileActions.ACTION_DELETE: {
                    AttachmentFileObject paramFileObject = (AttachmentFileObject) data.getSerializableExtra(AttachmentFileObject.RESULT);
                    for (AttachmentFileObject file : mFilesArray) {
                        if (file.file_id.equals(paramFileObject.file_id)) {
                            mFilesArray.remove(file);
                            adapter.notifyDataSetChanged();
                            break;
                        }
                    }
                    setResult(Activity.RESULT_OK);
                }

                case FileActions.ACTION_EDIT: {
                    AttachmentFileObject paramFileObject = (AttachmentFileObject) data.getSerializableExtra(AttachmentFileObject.RESULT);
                    upadateListItem(paramFileObject);
                    break;
                }

                case FileActions.ACTION_DOWNLOAD_OPEN: {
                    AttachmentFileObject paramFileObject = (AttachmentFileObject) data.getSerializableExtra(AttachmentFileObject.RESULT);
                    upadateListItem(paramFileObject);
                    jumpToDetail(paramFileObject);
                    break;
                }

//                default:
//                    showMiddleToast("");
            }
        }
    }

    private void upadateListItem(AttachmentFileObject paramFileObject) {
        for (int i = 0; i < mFilesArray.size(); ++i) {
            AttachmentFileObject file = mFilesArray.get(i);
            if (file.file_id.equals(paramFileObject.file_id)) {
                mFilesArray.set(i, paramFileObject);
                adapter.notifyDataSetChanged();
                break;
            }
        }
    }

    @OnActivityResult(AttachmentsActivity.RESULT_REQUEST_FILES)
    void onFolderResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            getNetwork(HOST_FILECOUNT, HOST_FILECOUNT);
        }
    }

    @OptionsItem
    void actionEdit() {
        doEdit();
    }

    private void doEdit() {
        if (mActionMode != null) {
            return;
        }

        mActionMode = startActionMode(mActionModeCallback);
        setListEditMode(true);
    }

    private void setListEditMode(boolean isEditMode) {
        this.isEditMode = isEditMode;
        adapter.notifyDataSetChanged();
    }

    /**
     * 删除选中的文件
     */
    void action_delete() {
        selectFile = new ArrayList<>();
        for (AttachmentFileObject fileObject : mFilesArray) {
            if (fileObject.isSelected && !fileObject.isFolder)
                selectFile.add(fileObject);
        }
        if (selectFile.size() == 0) {
            showButtomToast("没有选中文件");
            return;
        }
        String messageFormat = "确定要删除%s个文件么？";
        new AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
                .setTitle("删除文件")
                .setMessage(String.format(messageFormat, selectFile.size()))
                .setPositiveButton("确定", (dialog, which) -> deleteFiles())
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 删除单个选中的文件
     *
     * @param selectedFile 当前选中的文件
     */
    void action_delete_single(AttachmentFileObject selectedFile) {
        if (selectedFile == null) {
            showButtomToast("没有选中文件");
            return;
        }
        selectFile = new ArrayList<>();
        selectFile.add(selectedFile);

        final String messageFormat = "确定要删除文件 \"%s\" 么？";
        new AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
                .setTitle("删除文件")
                .setMessage(String.format(messageFormat, selectedFile.getName()))
                .setPositiveButton("确定", (dialog, which) -> deleteFiles())
                .setNegativeButton("取消", null)
                .show();
    }

    void deleteFiles() {
        String param = "";
        int i = 0;
        for (AttachmentFileObject file : selectFile) {
            if (i > 0) {
                param += "&";
            }
            param += "fileIds=" + file.file_id;
            i++;
        }
        if (selectFile.size() > 0) {
            deleteNetwork(String.format(HOST_FILE_DELETE, mProjectObjectId, param), HOST_FILE_DELETE);
        }
    }

    void action_all() {
        for (AttachmentFileObject fileObject : mFilesArray) {
            if (!fileObject.isFolder)
                fileObject.isSelected = true;
        }
        adapter.notifyDataSetChanged();
    }

    void action_inverse() {
        for (AttachmentFileObject fileObject : mFilesArray) {
            if (!fileObject.isFolder)
                fileObject.isSelected = !fileObject.isSelected;
        }
        adapter.notifyDataSetChanged();
    }

    void action_move() {
        selectFile = new ArrayList<>();
        for (AttachmentFileObject fileObject : mFilesArray) {
            if (fileObject.isSelected && !fileObject.isFolder)
                selectFile.add(fileObject);
        }
        //showButtomToast("selected count:" + selectFolder.size());
        if (selectFile.size() == 0) {
            showButtomToast("没有选中文件");
            return;
        }

        showMoveDialog();
    }

    void action_move_single(AttachmentFileObject selectedFile) {
        if (selectedFile == null) {
            showButtomToast("没有选中文件");
            return;
        }

        selectFile = new ArrayList<>();
        selectFile.add(selectedFile);

        if (selectedFile.isFolder) {
            showMoveDialog(RESULT_MOVE_FOLDER, selectedFile);
        } else {
            showMoveDialog();
        }
    }

    private void showMoveDialog() {
        showMoveDialog(FILE_MOVE_CODE, null);
    }

    private void showMoveDialog(int result, AttachmentFileObject attachmentFileObject) {
        AttachmentsFolderSelectorActivity_.intent(AttachmentsActivity.this)
                .mProjectObjectId(mProjectObjectId)
                .sourceFileObject(attachmentFileObject)
                .startForResult(result);
    }

    @OnActivityResult(FILE_MOVE_CODE)
    void onFileMoveResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            AttachmentFolderObject selectedFolder = (AttachmentFolderObject) data.getSerializableExtra("mAttachmentFolderObject");
            String param = "";
            if (selectedFolder.file_id.equals(mAttachmentFolderObject.file_id)) {
                return;
            }
            int i = 0;
            for (AttachmentFileObject file : selectFile) {
                if (i > 0) {
                    param += "&";
                }
                param += "fileId=" + file.file_id;
                i++;
            }

            putNetwork(String.format(HOST_FILE_MOVETO, mProjectObjectId, selectedFolder.file_id, param), null, HOST_FILE_MOVETO, -1, selectedFolder);
        }
    }

    @OnActivityResult(RESULT_MOVE_FOLDER)
    void onResultFolderMove(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            AttachmentFolderObject selectedFolder = (AttachmentFolderObject) data.getSerializableExtra("mAttachmentFolderObject");
            if (selectedFolder.file_id.equals(mAttachmentFolderObject.file_id)) {
                return;
            }

            if (selectFile.isEmpty()) {
                return;
            }

            AttachmentFileObject source = selectFile.get(selectFile.size() - 1);

            String host = String.format("%s/%s/folder/%s/move-to/%s", Global.HOST_API, mProject.getProjectPath(), source.file_id, selectedFolder.file_id);
            putNetwork(host, null, TAG_MOVE_FOLDER, -1, selectedFolder);
        }
    }

    public void initBottomPop() {
        if (mAttachmentPopupWindow == null) {
            ArrayList<DialogUtil.BottomPopupItem> popupItemArrayList = new ArrayList<>();
            DialogUtil.BottomPopupItem renameItem = new DialogUtil.BottomPopupItem("重命名", R.drawable.ic_popup_attachment_rename);
            popupItemArrayList.add(renameItem);
            DialogUtil.BottomPopupItem deleteItem = new DialogUtil.BottomPopupItem("删除", R.drawable.ic_popup_attachment_delete_selector);
            popupItemArrayList.add(deleteItem);
            mAttachmentPopupWindow = DialogUtil.initBottomPopupWindow(AttachmentsActivity.this, "", popupItemArrayList, onPopupItemClickListener);
        }

        if (mAttachmentFilePopupWindow == null) {
            ArrayList<DialogUtil.BottomPopupItem> popupItemArrayList = new ArrayList<>();
            DialogUtil.BottomPopupItem moveItem = new DialogUtil.BottomPopupItem("移动", R.drawable.ic_popup_attachment_move_selector);
            popupItemArrayList.add(moveItem);

            DialogUtil.BottomPopupItem downloadItem = new DialogUtil.BottomPopupItem("下载", R.drawable.ic_popup_attachment_download_selector);
            popupItemArrayList.add(downloadItem);

            DialogUtil.BottomPopupItem deleteItem = new DialogUtil.BottomPopupItem("删除", R.drawable.ic_popup_attachment_delete_selector);
            popupItemArrayList.add(deleteItem);
            mAttachmentFilePopupWindow = DialogUtil.initBottomPopupWindow(AttachmentsActivity.this, "", popupItemArrayList, onFilePopupItemClickListener);
        }
    }

    public void showPop(View view, int position) {
        if (mAttachmentPopupWindow == null) {
            initBottomPop();
        }
        selectedPosition = position;
        AttachmentFileObject selectedFileObject = mFilesArray.get(selectedPosition);
        if (selectedFileObject.isFolder) {
            AttachmentFolderObject selectedFolderObject = selectedFileObject.folderObject;

            String[] itemNames;
            if (selectedFolderObject.file_id.equals("0")) {
                itemNames = new String[]{};
            } else if (selectedFolderObject.count != 0) {
                itemNames = new String[]{"重命名", "移动到"};
            } else {
                itemNames = new String[]{"重命名", "移动到", "删除"};
            }
            if (itemNames.length == 0) {
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MyAlertDialogStyle);
            builder.setItems(itemNames, (dialog, which) -> {
                if (which == 0) {
                    doRename(selectedPosition, mFilesArray.get(selectedPosition).folderObject);
                } else if (which == 1) {
                    action_move_single(mFilesArray.get(selectedPosition));
                } else {
                    AttachmentFolderObject selectedFolderObject1 = mFilesArray.get(selectedPosition).folderObject;
                    if (selectedFolderObject1.isDeleteable()) {
                        action_delete_single(selectedFolderObject1);
                    } else {
                        showButtomToast("请先清空文件夹");
                    }
                }
            });
            builder.show();

        } else {
            if (selectedFileObject.downloadId != 0L) {
                return;
            }

            String[] itemTitles;
            if (selectedFileObject.isOwner()) {
                itemTitles = new String[]{"重命名", "移动到", "删除"};
            } else {
                itemTitles = new String[]{"重命名", "移动到"};
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MyAlertDialogStyle);
            builder.setItems(itemTitles, (dialog, which) -> {
                if (which == 0) {
                    fileRename(selectedPosition, mFilesArray.get(selectedPosition));
                } else if (which == 1) {
                    action_move_single(mFilesArray.get(selectedPosition));
                } else {
                    action_delete_single(mFilesArray.get(selectedPosition));
                }
            });
            builder.show();
        }
    }

    /**
     * 是否选中了别人创建的文件，别人创建的文件，没有权限删除
     *
     * @return 是否选中了别人创建的文件
     */
    private boolean isChooseOthers() {
        boolean hasOtherFile = false;
        for (AttachmentFileObject fileObject : mFilesArray) {
            if (fileObject.isSelected && !fileObject.isFolder && !fileObject.isOwner()) {
                hasOtherFile = true;
                break;
            }
        }
        return hasOtherFile;
    }

    private void doRename(final int position, final AttachmentFolderObject folderObject) {
        if (folderObject.file_id.equals("0")) {
            showButtomToast("默认文件夹无法重命名");
            return;
        }
        LayoutInflater li = LayoutInflater.from(AttachmentsActivity.this);
        View v1 = li.inflate(R.layout.dialog_input, null);
        final EditText input = (EditText) v1.findViewById(R.id.value);
        input.setText(folderObject.name);
        new AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
                .setTitle("重命名")
                .setView(v1)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newName = input.getText().toString();
                    //从网页版扒来的正则
                    String namePatternStr = "[,`~!@#$%^&*:;()'\"><|.\\ /=]";
                    Pattern namePattern = Pattern.compile(namePatternStr);
                    if (newName.equals("")) {
                        showButtomToast("名字不能为空");
                    } else if (namePattern.matcher(newName).find()) {
                        showButtomToast("文件夹名：" + newName + " 不能采用");
                    } else {
                        if (!newName.equals(folderObject.name)) {
                            HOST_FOLDER_NAME = String.format(HOST_FOLDER_NAME, mProjectObjectId, folderObject.file_id, newName);
                            putNetwork(HOST_FOLDER_NAME, HOST_FOLDER_NAME, position, newName);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();

        input.requestFocus();
    }


    private void fileRename(final int postion, final AttachmentFileObject folderObject) {
        LayoutInflater li = LayoutInflater.from(AttachmentsActivity.this);
        View v1 = li.inflate(R.layout.dialog_input, null);
        final EditText input = (EditText) v1.findViewById(R.id.value);
        input.setText(folderObject.getName());
        new AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
                .setTitle("重命名")
                .setView(v1)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newName = input.getText().toString();
                    if (newName.equals("")) {
                        showButtomToast("名字不能为空");
                    } else {
                        if (!newName.equals(folderObject.getName())) {
                            String urlTemplate = Global.HOST_API + "/project/%d/files/%s/rename";
                            String url = String.format(urlTemplate, mProjectObjectId, folderObject.file_id);
                            RequestParams params = new RequestParams();
                            params.put("name", newName);
                            putNetwork(url, params, HOST_HTTP_FILE_RENAME, postion, newName);
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();

        input.requestFocus();
    }

    void action_delete_single(AttachmentFolderObject selectedFolderObject) {
        if (selectedFolderObject == null)
            return;

        selectFolder = new ArrayList<>();
        selectFolder.add(selectedFolderObject);
        String messageFormat = "确定删除文件夹 \"%s\" ？";
        showDialog("删除文件夹", String.format(messageFormat, selectedFolderObject.name),
                (dialog, which) -> deleteFolders());
    }

    void deleteFolders() {
        if (selectFolder.size() > 0) {
            //setRefreshing(true);
            HOST_FOLDER_DELETE = String.format(HOST_FOLDER_DELETE_FORMAT, mProjectObjectId, selectFolder.get(0).file_id);
            deleteNetwork(HOST_FOLDER_DELETE, HOST_FOLDER_DELETE, selectedPosition, selectFolder.get(0));
        }
    }

    protected final void common_folder_bottom_add() {
        LayoutInflater li = LayoutInflater.from(AttachmentsActivity.this);
        View v1 = li.inflate(R.layout.dialog_input, null);
        final EditText input = (EditText) v1.findViewById(R.id.value);
        input.setHint("请输入文件夹名称");
        new AlertDialog.Builder(this, R.style.MyAlertDialogStyle)
                .setTitle("新建文件夹")
                .setView(v1)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newName = input.getText().toString();
                    String namePatternStr = "[,`~!@#$%^&*:;()'\"><|.\\ /=]";
                    Pattern namePattern = Pattern.compile(namePatternStr);
                    if (newName.equals("")) {
                        showButtomToast("名字不能为空");
                    } else if (namePattern.matcher(newName).find()) {
                        showButtomToast("文件夹名：" + newName + " 不能采用");
                    } else {
                        HOST_FOLDER_NEW = String.format(HOST_FOLDER_NEW, mProjectObjectId);
                        RequestParams params = new RequestParams();
                        params.put("name", newName);
                        params.put("parentId", mAttachmentFolderObject.file_id);
                        postNetwork(HOST_FOLDER_NEW, params, HOST_FOLDER_NEW);
                    }
                }).setNegativeButton("取消", null)
                .show();

        input.requestFocus();
    }

    @Override
    public String getProjectPath() {
        return "/project/" + mProjectObjectId;
    }

    private void setDownloadStatus(AttachmentFileObject mFileObject) {
        Long downloadId = getDownloadId(mFileObject);
        if (downloadId != 0L) {
            mFileObject.downloadId = downloadId;
            updateFileDownloadStatus(mFileObject);
            mFileObject.isDownload = false;
        } else {
            File mFile = FileUtil.getDestinationInExternalPublicDir(getFileDownloadPath(), mFileObject.getSaveName(mProjectObjectId));
            if (mFile.exists() && mFile.isFile()) {
                mFileObject.isDownload = true;
            } else {
                mFileObject.downloadId = 0L;
                mFileObject.isDownload = false;
            }
        }
    }

    // 更新文件的下载状态
    @Override
    public void checkFileDownloadStatus() {
        for (AttachmentFileObject fileObject : mFilesArray) {
            if (!fileObject.isFolder) {
                setDownloadStatus(fileObject);
                //Log.d("onResume", "update status:" + fileObject.name + " " + fileObject.isDownload);
            }
        }
        adapter.notifyDataSetChanged();
    }

    protected String getLink() {
        if (mProject == null) {
            showButtomToast("获取项目信息失败，请稍后重试");
            getNetwork(String.format(HOST_PROJECT_ID, mProjectObjectId), HOST_PROJECT_ID);
            return "";
        }

        return mProject.getPath() + "/attachment/" + mAttachmentFolderObject.file_id;
    }

    public void initRightTopPop() {
        if (mRightTopPopupWindow == null) {
            ArrayList<DialogUtil.RightTopPopupItem> popupItemArrayList = new ArrayList<>();
            DialogUtil.RightTopPopupItem moveItem = new DialogUtil.RightTopPopupItem(getString(R.string.action_move), R.drawable.ic_popup_attachment_move);
            popupItemArrayList.add(moveItem);
            DialogUtil.RightTopPopupItem downloadItem = new DialogUtil.RightTopPopupItem(getString(R.string.action_download), R.drawable.ic_popup_attachment_download);
            popupItemArrayList.add(downloadItem);
            DialogUtil.RightTopPopupItem deleteItem = new DialogUtil.RightTopPopupItem(getString(R.string.action_delete), R.drawable.ic_menu_delete_selector);
            popupItemArrayList.add(deleteItem);
            mRightTopPopupWindow = DialogUtil.initRightTopPopupWindow(AttachmentsActivity.this, popupItemArrayList, onRightTopPopupItemClickListener);
        }
    }

    @Override
    public int getProjectId() {
        return mProjectObjectId;
    }

    public void showRightTopPop() {

        if (mRightTopPopupWindow == null) {
            initRightTopPop();
        }

        DialogUtil.RightTopPopupItem moveItem = mRightTopPopupWindow.adapter.getItem(0);
        DialogUtil.RightTopPopupItem deleteItem = mRightTopPopupWindow.adapter.getItem(2);

        deleteItem.enabled = !isChooseOthers();

        mRightTopPopupWindow.adapter.notifyDataSetChanged();

        Rect rectgle = new Rect();
        Window window = getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rectgle);
        int StatusBarHeight = rectgle.top;
        int contentViewTop =
                window.findViewById(Window.ID_ANDROID_CONTENT).getTop();
        //int TitleBarHeight= contentViewTop - StatusBarHeight;
        mRightTopPopupWindow.adapter.notifyDataSetChanged();
        mRightTopPopupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        mRightTopPopupWindow.showAtLocation(getCurrentFocus(), Gravity.TOP | Gravity.RIGHT, 0, contentViewTop);
    }

    private enum UploadStatus {
        Uploading, Finish, Close, Failure
    }

    public class FileActions {
        public static final String ACTION_NAME = "ACTION_NAME";
        public static final int ACTION_EDIT = 1;
        public static final int ACTION_DELETE = 2;
        public static final int ACTION_DOWNLOAD_OPEN = 4;
    }

}
