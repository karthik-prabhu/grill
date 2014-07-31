var codeMirror = CodeMirror.fromTextArea(document.getElementById("query"), {
    mode: "text/x-sql",
    lineWrapping: true
 });

var util = new Util;
var session = new Session;
var historyView = new HistoryView;

var loadPage = function() {
	//Hidden by default
	$("#query-form .loading").hide();
	$("#queryui, #loginui, #historyui").hide();
	$("#navlinks .active").removeClass("active");
	
	var page = window.location.hash.substr(1);
	if(!session.isLoggedIn()) {
		//Show login UI
		$("#loginui").show();
	}
	else if(page === "history") {
		$("#queryui").show();
		$("#query-ui-content").hide();
		$("#historyui").show();
		$("#navlinks li").last().addClass("active");
		session.getAllQueries(function(data) {
			for(var i = 0; i < data.length; i++) {
				var query = new Query(data[i]["handleId"]);
				var queryView = new QueryListView(query);
				historyView.addQueryListView(queryView);
				// query.setOnUpdatedListener(historyView.refreshView);
			}
		});
	}
	else {
		$("#queryui").show();
		$("#query-ui-content").show();
		$("#navlinks li").first().addClass("active");
	}
}
loadPage();

var setEnableForm = function(enable) {
	codeMirror.setOption("readOnly", !enable);
	codeMirror.setOption("nocursor", !enable);
	$("#query-form button").attr("disabled", !enable);
	if(enable)
		$("#query-form .loading").hide();
	else
		$("#query-form .loading").show();
}

var QueryStatusView = function(query) {
	var id = "query-status-view-" + QueryStatusView.instanceNo++;
	var model = query;
	var text = model.getStatusMessage();

	this.updateView = function() {
		text = model.getStatusMessage();
		$("#" + id).text(text);
	}
	
	this.getView = function() {
		return $("<span>", {id: id, text: text});
	}
};
QueryStatusView.instanceNo = 0;

var TableResultView = function() {
	var id = "table-result-view-" + TableResultView.instanceNo++;
	var rows = [];

	this.updateView = function(rows) {
		$("#" + id).empty();

		if(rows && rows.length <= 0)
			return;

		//Add header
		$("#" + id).append($("<thead>").append($("<tr>")));
		for(var i = 0; i < rows[0].getColumns().length; i++) {
			$("#" + id + " thead tr").append($("<th>", {text: rows[0].getColumns()[i]}));
		};

		//Add body
		$("#" + id).append($("<tbody>"));
		for(var i = 1; i < rows.length; i++) {
			var tRow = $("<tr>");
			var columns = rows[i].getColumns();
			for(var j = 0; j < columns.length; j++) {
				tRow.append($("<td>", {text: columns[j]}));
			};
			$("#" + id + " tbody").append(tRow);
		};
	}

	this.getView = function() {
		return $("<table>", {id: id, class: "table table-bordered"});
	}
};
TableResultView.instanceNo = 0;

$("#query-form").submit(function(event) {
	event.preventDefault(); 

	//Disable UI components
	setEnableForm(false);

	var query = $("#query").val().trim();

	//Perform basic checks
	if(query) {
		session.submitQuery(query, function(queryObj) {
			if(queryObj) {
				var queryStatusView = new QueryStatusView(queryObj);
				if($("#query-form .loading").next().length > 0)
					$("#query-form .loading").next().remove();
				$("#query-form .loading").after(queryStatusView.getView());

				queryObj.setOnUpdatedListener(queryStatusView.updateView);
				queryObj.setOnCompletedListener(function() {
					setEnableForm(true);
					//Display results
					console.log("Completed");
					if(queryObj.getQueryStatus() === "SUCCESSFUL") {
						var resultView = new TableResultView;
						$("#query-form").next().after(resultView.getView());

						var rs = queryObj.getResultSet();
						rs.getNextRows(function(rows) {
							console.log("Got next rows");
							resultView.updateView(rows);
						});
					}
				});
			}
			else {
				//Problem submitting query. Reset UI and display message
				setEnableForm(true);
			}
		});
	}
	else {
		//No query. Reset UI
		setEnableForm(true);
	}
});

$("#login-form").submit(function(event){
	event.preventDefault();

	var email = $("#email").val();
	var password = $("#password").val();

	if(!email) {
		$("#email").addClass("error");
		return;
	}
	$("#email").removeClass("error");

	if(!password) {
		$("#password").addClass("error");
		return;
	}
	$("#password").removeClass("error");

	$("#email, #password, #login-btn").attr("disabled", true);

	session.logIn(email, password, function() {
		window.location.reload();
	});
});

$("#navlinks li a").click(function(event) {
	event.preventDefault();
	window.location.hash = this.hash;
	loadPage();
});