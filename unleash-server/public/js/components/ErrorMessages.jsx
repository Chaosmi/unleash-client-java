var React = require('react');

var ErrorMessages = React.createClass({
    render: function() {
        if (!this.props.errors.length) {
            return <div/>;
        }

        var errorNodes = this.props.errors.map(function(e) {
            return (<li key={e} className="largetext">{e}</li>);
        });

        return (
            <div className="container">
            <div className="mod shadow mtm">
              <div className="inner bg-red-lt">
                <div className="bd">
                  <div className="media centerify">
	             <div className="imgExt">
                        <a
                           onClick={this.props.onClearErrors}
                           className="icon-kryss1 linkblock sharp">
                        </a>
                     </div>
	             <div className="bd">
                        <ul>{errorNodes}</ul>
                     </div>
                  </div>
                </div>
              </div>
            </div>
            </div>
        );
    }
});

module.exports = ErrorMessages;

